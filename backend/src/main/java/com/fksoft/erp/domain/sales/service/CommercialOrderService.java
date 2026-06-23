package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.exception.OpportunityCannotBeMarkedWonException;
import com.fksoft.erp.domain.crm.exception.OpportunityNotFoundException;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.sales.exception.CommercialOrderAccessDeniedException;
import com.fksoft.erp.domain.sales.exception.CommercialOrderAlreadyExistsException;
import com.fksoft.erp.domain.sales.exception.CommercialOrderNotFoundException;
import com.fksoft.erp.domain.sales.exception.ProposalAccessDeniedException;
import com.fksoft.erp.domain.sales.exception.ProposalNotFoundException;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderCreated;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.OrderIndicatorQueries;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.domain.sales.service.data.CommercialOrderDetail;
import com.fksoft.erp.domain.sales.service.data.CommercialOrderListItem;
import com.fksoft.erp.domain.sales.service.data.CommercialOrderSearchCriteria;
import com.fksoft.erp.domain.sales.service.data.OrderIndicators;
import com.fksoft.erp.domain.workflow.WorkflowContext;
import com.fksoft.erp.domain.workflow.WorkflowEngine;
import com.fksoft.erp.domain.workflow.WorkflowState;
import com.fksoft.erp.domain.workflow.WorkflowStateRepository;
import com.fksoft.erp.domain.workflow.WorkflowTransitionNotAllowedException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service for Commercial Orders (Sales &amp; Proposals): creates a Commercial Order from an
 * Accepted Proposal (snapshotting it and closing the source Opportunity as won) and serves its detail. One
 * service per area handles command and reads. Creating an Order never creates a Booking, Receivable, Payment,
 * Commission or Customer Care record.
 */
@Service
@RequiredArgsConstructor
public class CommercialOrderService {

    // An Order counts against the "one active Order per Proposal" rule while it is not cancelled.
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING_BOOKING", "BOOKING_NOT_REQUIRED");

    /** The workflow definition code for the Commercial Order lifecycle. */
    private static final String ORDER_WORKFLOW = "order";

    private final CommercialOrderRepository orders;
    private final OrderAccessPolicy accessPolicy;
    private final OrderIndicatorQueries indicatorQueries;
    private final ProposalRepository proposals;
    private final ProposalAccessPolicy proposalAccessPolicy;
    private final OpportunityRepository opportunities;
    private final LeadRepository leads;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final WorkflowEngine workflow;
    private final WorkflowStateRepository workflowStates;

    /**
     * Creates a Commercial Order from an Accepted Proposal the caller is allowed to see, marks the source
     * Opportunity as won, and returns the new order id. A Proposal has at most one active Order. The Order
     * snapshots the Proposal's items, total and source references; it creates no Booking, Receivable, Payment
     * or Commission data, and the Opportunity closing creates no Finance or Booking behavior.
     *
     * @param proposalId the source proposal id
     * @param userId the authenticated user
     * @param canSeeAllProposals whether the caller may see every Proposal
     * @param canSeeUnassignedProposals whether the caller may also see the unassigned Proposal pool
     * @return the new order id
     * @throws ProposalNotFoundException if the source Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see the source Proposal
     * @throws com.fksoft.erp.domain.sales.exception.ProposalNotAcceptedException if the Proposal is not Accepted
     * @throws CommercialOrderAlreadyExistsException if the Proposal already has an active Order
     */
    @Transactional
    public UUID create(UUID proposalId, UUID userId, boolean canSeeAllProposals, boolean canSeeUnassignedProposals) {
        Proposal proposal = proposals.findById(proposalId).orElseThrow(ProposalNotFoundException::new);
        if (!proposalAccessPolicy.canSee(proposal, userId, canSeeAllProposals, canSeeUnassignedProposals)) {
            throw new ProposalAccessDeniedException();
        }
        orders.findFirstByProposalIdAndStatusIn(proposalId, ACTIVE_STATUSES).ifPresent(existing -> {
            throw new CommercialOrderAlreadyExistsException(existing.id());
        });
        WorkflowState pendingBooking = orderState("PENDING_BOOKING");
        WorkflowState bookingNotRequired = orderState("BOOKING_NOT_REQUIRED");
        CommercialOrder order = CommercialOrder.createFromProposal(
                proposal, userId, orders.nextOrderNumber(), pendingBooking, bookingNotRequired);
        orders.save(order);
        // Close the source Opportunity as won (same transaction); this creates no Finance or Booking behavior.
        Opportunity opportunity =
                opportunities.findById(proposal.opportunityId()).orElseThrow(OpportunityNotFoundException::new);
        WorkflowState wonState;
        try {
            wonState = workflow.apply(
                    "opportunity", opportunity.currentState(), "win", WorkflowContext.of(opportunity, userId));
        } catch (WorkflowTransitionNotAllowedException e) {
            throw new OpportunityCannotBeMarkedWonException();
        }
        opportunity.applyWin(wonState, userId);
        opportunities.save(opportunity);
        events.publishEvent(new CommercialOrderCreated(
                order.id(),
                proposal.id(),
                proposal.opportunityId(),
                proposal.leadId(),
                userId,
                proposal.responsiblePersonId()));
        return order.id();
    }

    /**
     * Full detail of a Commercial Order the caller is allowed to see, with the source Proposal, Opportunity
     * and Lead kept traceable.
     *
     * @param id the order id
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Order
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the detail read model
     * @throws CommercialOrderNotFoundException if the Order does not exist
     * @throws CommercialOrderAccessDeniedException if the caller may not see it
     */
    @Transactional(readOnly = true)
    public CommercialOrderDetail detail(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        return toDetail(loadVisible(id, userId, canSeeAll, canSeeUnassigned));
    }

    /**
     * Operational, paginated Commercial Order list, filtered by the given criteria and restricted to the
     * caller's visibility. Cancelled Orders are excluded unless the status filter explicitly includes them.
     * Enriches each item with the responsible's name, the source Proposal's title (the client-facing summary)
     * and the source Opportunity's name. Exposes commercial-order data only.
     *
     * @param criteria the optional filters
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Order
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return a page of Commercial Order list items
     */
    @Transactional(readOnly = true)
    public Page<CommercialOrderListItem> list(
            CommercialOrderSearchCriteria criteria,
            Pageable pageable,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        Specification<CommercialOrder> spec = CommercialOrderSpecifications.matching(criteria)
                .and(accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned));
        Page<CommercialOrder> page = orders.findAll(spec, pageable);
        Map<UUID, String> names = resolveNames(page.getContent().stream().map(CommercialOrder::responsiblePersonId));
        Map<UUID, String> proposalTitles =
                resolveProposalTitles(page.getContent().stream().map(CommercialOrder::proposalId));
        Map<UUID, String> opportunityNames =
                resolveOpportunityNames(page.getContent().stream().map(CommercialOrder::opportunityId));
        return page.map(o -> CommercialOrderListItem.from(
                o,
                proposalTitles.get(o.proposalId()),
                opportunityNames.get(o.opportunityId()),
                nameOf(names, o.responsiblePersonId())));
    }

    /**
     * Minimum Commercial Order indicators over the Orders visible to the caller. The volume figures (total,
     * total amount, by responsible) cover the requested period (by creation date); the operational figure
     * (pending booking) is a current snapshot of all the visible Orders. Read-only; never exposes Booking,
     * Receivable, Payment, Commission or Customer Care data.
     *
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Order
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return the indicators
     */
    @Transactional(readOnly = true)
    public OrderIndicators indicators(
            UUID userId, boolean canSeeAll, boolean canSeeUnassigned, Instant from, Instant to) {
        Specification<CommercialOrder> visible = accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned);

        // Volume — over the period.
        Map<String, Long> countByStatus = indicatorQueries.countByStatus(visible, from, to);
        long total = countByStatus.values().stream().mapToLong(Long::longValue).sum();
        BigDecimal totalAmount = indicatorQueries.sumTotal(visible, from, to);
        Map<UUID, Long> byResponsibleId = indicatorQueries.countByResponsible(visible, from, to);

        // Operational — current snapshot (no period).
        long pendingBooking =
                indicatorQueries.countByStatus(visible, null, null).getOrDefault("PENDING_BOOKING", 0L);

        Map<UUID, String> names = resolveNames(byResponsibleId.keySet().stream());
        List<OrderIndicators.ResponsibleCount> responsibleCounts = byResponsibleId.entrySet().stream()
                .map(e -> new OrderIndicators.ResponsibleCount(nameOf(names, e.getKey()), e.getValue()))
                .toList();

        return new OrderIndicators(total, totalAmount, responsibleCounts, pendingBooking);
    }

    private CommercialOrderDetail toDetail(CommercialOrder order) {
        Proposal proposal = proposals.findById(order.proposalId()).orElseThrow(ProposalNotFoundException::new);
        Opportunity opportunity =
                opportunities.findById(order.opportunityId()).orElseThrow(OpportunityNotFoundException::new);
        Lead lead = leads.findById(order.leadId()).orElseThrow(LeadNotFoundException::new);
        Map<UUID, String> names = resolveNames(Stream.of(order.responsiblePersonId(), order.createdBy()));
        return CommercialOrderDetail.from(order, proposal, opportunity, lead, names);
    }

    private WorkflowState orderState(String code) {
        return workflowStates
                .findByDefinition_CodeAndCode(ORDER_WORKFLOW, code)
                .orElseThrow(() -> new IllegalStateException("Missing Commercial Order workflow state: " + code));
    }

    private CommercialOrder loadVisible(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        CommercialOrder order = orders.findById(id).orElseThrow(CommercialOrderNotFoundException::new);
        if (!accessPolicy.canSee(order, userId, canSeeAll, canSeeUnassigned)) {
            throw new CommercialOrderAccessDeniedException();
        }
        return order;
    }

    private Map<UUID, String> resolveNames(Stream<UUID> ids) {
        Set<UUID> set = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : users.findAllById(set).stream().collect(Collectors.toMap(User::id, User::username));
    }

    private Map<UUID, String> resolveProposalTitles(Stream<UUID> ids) {
        Set<UUID> set = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : proposals.findAllById(set).stream().collect(Collectors.toMap(Proposal::id, Proposal::title));
    }

    private Map<UUID, String> resolveOpportunityNames(Stream<UUID> ids) {
        Set<UUID> set = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : opportunities.findAllById(set).stream().collect(Collectors.toMap(Opportunity::id, Opportunity::name));
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }
}
