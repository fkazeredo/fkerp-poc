package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
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
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.domain.sales.service.data.CommercialOrderDetail;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private static final Set<CommercialOrderStatus> ACTIVE_STATUSES =
            Set.of(CommercialOrderStatus.PENDING_BOOKING, CommercialOrderStatus.BOOKING_NOT_REQUIRED);

    private final CommercialOrderRepository orders;
    private final OrderAccessPolicy accessPolicy;
    private final ProposalRepository proposals;
    private final ProposalAccessPolicy proposalAccessPolicy;
    private final OpportunityRepository opportunities;
    private final LeadRepository leads;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

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
        CommercialOrder order = CommercialOrder.createFromProposal(proposal, userId);
        orders.save(order);
        // Close the source Opportunity as won (same transaction); this creates no Finance or Booking behavior.
        Opportunity opportunity =
                opportunities.findById(proposal.opportunityId()).orElseThrow(OpportunityNotFoundException::new);
        opportunity.markWon(userId);
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

    private CommercialOrderDetail toDetail(CommercialOrder order) {
        Proposal proposal = proposals.findById(order.proposalId()).orElseThrow(ProposalNotFoundException::new);
        Opportunity opportunity =
                opportunities.findById(order.opportunityId()).orElseThrow(OpportunityNotFoundException::new);
        Lead lead = leads.findById(order.leadId()).orElseThrow(LeadNotFoundException::new);
        Map<UUID, String> names = resolveNames(Stream.of(order.responsiblePersonId(), order.createdBy()));
        return CommercialOrderDetail.from(order, proposal, opportunity, lead, names);
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
}
