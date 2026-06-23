package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.exception.OpportunityAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.OpportunityNotFoundException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.service.OpportunityAccessPolicy;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.reference.ReferenceData;
import com.fksoft.erp.domain.sales.exception.CustomerRejectionReasonNotAvailableException;
import com.fksoft.erp.domain.sales.exception.OpportunityNotReadyForProposalException;
import com.fksoft.erp.domain.sales.exception.ProposalAccessDeniedException;
import com.fksoft.erp.domain.sales.exception.ProposalAlreadyExistsForOpportunityException;
import com.fksoft.erp.domain.sales.exception.ProposalNotApprovedException;
import com.fksoft.erp.domain.sales.exception.ProposalNotEditableException;
import com.fksoft.erp.domain.sales.exception.ProposalNotFoundException;
import com.fksoft.erp.domain.sales.exception.ProposalNotSentException;
import com.fksoft.erp.domain.sales.exception.ProposalNotUnderReviewException;
import com.fksoft.erp.domain.sales.exception.ProposalRejectionReasonNotAvailableException;
import com.fksoft.erp.domain.sales.exception.SendingChannelNotAvailableException;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CustomerRejectionReason;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalCreated;
import com.fksoft.erp.domain.sales.model.ProposalRejectionReason;
import com.fksoft.erp.domain.sales.model.ProposalStatusChange;
import com.fksoft.erp.domain.sales.model.SendingChannel;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.CustomerRejectionReasonRepository;
import com.fksoft.erp.domain.sales.repository.ProposalIndicatorQueries;
import com.fksoft.erp.domain.sales.repository.ProposalRejectionReasonRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.domain.sales.repository.SendingChannelRepository;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalDetail;
import com.fksoft.erp.domain.sales.service.data.ProposalIndicators;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalListItem;
import com.fksoft.erp.domain.sales.service.data.ProposalSearchCriteria;
import com.fksoft.erp.domain.sales.service.data.UpdateProposalCommand;
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
 * Application Service for commercial Proposals (Sales &amp; Proposals): creates a Proposal from a
 * READY_FOR_PROPOSAL Opportunity and serves its detail/list. One service per area handles both command
 * and reads. Never creates a Sale, Sales Order, Booking, Customer, Financial, Payment or Commission
 * record, and never modifies the source Opportunity or Lead.
 */
@Service
@RequiredArgsConstructor
public class ProposalService {

    // Statuses for which a Proposal is still "open" — at most one open Proposal per Opportunity.
    private static final Set<String> OPEN_STATUSES =
            Set.of("DRAFT", "READY_FOR_REVIEW", "APPROVED", "SENT", "ACCEPTED");

    // Status codes for which a Commercial Order is still active — used to surface the Proposal's active Order.
    private static final Set<String> ACTIVE_ORDER_STATUSES = Set.of("PENDING_BOOKING", "BOOKING_NOT_REQUIRED");

    private final ProposalRepository proposals;
    private final ProposalAccessPolicy accessPolicy;
    private final ProposalIndicatorQueries indicatorQueries;
    private final OpportunityRepository opportunities;
    private final OpportunityAccessPolicy opportunityAccessPolicy;
    private final LeadRepository leads;
    private final UserRepository users;
    private final CommercialOrderRepository orders;
    private final ApplicationEventPublisher events;
    private final WorkflowEngine workflow;
    private final WorkflowStateRepository workflowStates;
    private final ProposalRejectionReasonRepository rejectionReasons;
    private final CustomerRejectionReasonRepository customerRejectionReasons;
    private final SendingChannelRepository sendingChannels;
    private final ProposalItemTypeService itemTypes;

    /** The workflow definition code for the Proposal lifecycle. */
    private static final String PROPOSAL_WORKFLOW = "proposal";

    /**
     * Creates a Proposal from a READY_FOR_PROPOSAL Opportunity the caller is allowed to see. An
     * Opportunity has at most one open Proposal at a time; the responsible defaults to the Opportunity's,
     * an explicit one (when active) overrides it. The Opportunity is not modified, and no Sale, Order,
     * Booking or Financial data is created.
     *
     * @param command the proposal data (source opportunity + client-facing fields)
     * @param createdBy id of the authenticated user
     * @param canSeeAllOpportunities whether the caller may see every Opportunity
     * @param canSeeUnassignedOpportunities whether the caller may also see the unassigned Opportunity pool
     * @return the new proposal id
     * @throws OpportunityNotFoundException if the source Opportunity does not exist
     * @throws OpportunityAccessDeniedException if the caller may not see the source Opportunity
     * @throws OpportunityNotReadyForProposalException if the Opportunity is not READY_FOR_PROPOSAL
     * @throws ProposalAlreadyExistsForOpportunityException if the Opportunity already has an open Proposal
     * @throws ResponsiblePersonNotFoundException if a responsible is given but unknown/inactive
     */
    @Transactional
    public UUID create(
            CreateProposalCommand command,
            UUID createdBy,
            boolean canSeeAllOpportunities,
            boolean canSeeUnassignedOpportunities) {
        Opportunity opportunity =
                opportunities.findById(command.opportunityId()).orElseThrow(OpportunityNotFoundException::new);
        if (!opportunityAccessPolicy.canSee(
                opportunity, createdBy, canSeeAllOpportunities, canSeeUnassignedOpportunities)) {
            throw new OpportunityAccessDeniedException();
        }
        if (!"READY_FOR_PROPOSAL".equals(opportunity.stage())) {
            throw new OpportunityNotReadyForProposalException();
        }
        proposals
                .findFirstByOpportunityIdAndStatusIn(opportunity.id(), OPEN_STATUSES)
                .ifPresent(existing -> {
                    throw new ProposalAlreadyExistsForOpportunityException(existing.id());
                });
        UUID responsibleId;
        if (command.responsiblePersonId() != null) {
            if (users.findById(command.responsiblePersonId())
                    .filter(User::active)
                    .isEmpty()) {
                throw new ResponsiblePersonNotFoundException();
            }
            responsibleId = command.responsiblePersonId();
        } else {
            // The opportunity's responsible is preserved by default.
            responsibleId = opportunity.responsiblePersonId();
        }
        WorkflowState initialState = workflowStates
                .findByDefinition_CodeAndCode(PROPOSAL_WORKFLOW, "DRAFT")
                .orElseThrow(() -> new IllegalStateException("Missing Proposal workflow initial state"));
        Proposal proposal =
                Proposal.createFromOpportunity(opportunity, responsibleId, command, initialState, createdBy);
        proposals.save(proposal);
        events.publishEvent(
                new ProposalCreated(proposal.id(), opportunity.id(), opportunity.leadId(), createdBy, responsibleId));
        return proposal.id();
    }

    /**
     * Full detail of a Proposal the caller is allowed to see, with the source Opportunity kept traceable.
     *
     * @param id the proposal id
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the detail read model
     * @throws ProposalNotFoundException if the Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see it
     */
    @Transactional(readOnly = true)
    public ProposalDetail detail(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        return toDetail(loadVisible(id, userId, canSeeAll, canSeeUnassigned));
    }

    /**
     * Adds an item to a Draft Proposal the caller is allowed to see, and returns the refreshed detail
     * (with the recomputed total). Creates no Booking, Financial or Commission data.
     *
     * @param proposalId the proposal id
     * @param command the item data
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws ProposalNotFoundException if the Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.sales.exception.ProposalNotEditableException if it is not a Draft
     * @throws com.fksoft.erp.domain.sales.exception.ProposalItemInvalidException if the discount is invalid
     */
    @Transactional
    public ProposalDetail addItem(
            UUID proposalId, ProposalItemCommand command, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Proposal proposal = loadVisible(proposalId, userId, canSeeAll, canSeeUnassigned);
        proposal.addItem(itemTypes.requireActive(command.typeId()), command, userId);
        return toDetail(proposals.saveAndFlush(proposal));
    }

    /**
     * Updates an item of a Draft Proposal the caller is allowed to see, and returns the refreshed detail.
     *
     * @param proposalId the proposal id
     * @param itemId the item id
     * @param command the new item data
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws ProposalNotFoundException if the Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.sales.exception.ProposalNotEditableException if it is not a Draft
     * @throws com.fksoft.erp.domain.sales.exception.ProposalItemNotFoundException if the item is unknown
     * @throws com.fksoft.erp.domain.sales.exception.ProposalItemInvalidException if the discount is invalid
     */
    @Transactional
    public ProposalDetail updateItem(
            UUID proposalId,
            UUID itemId,
            ProposalItemCommand command,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        Proposal proposal = loadVisible(proposalId, userId, canSeeAll, canSeeUnassigned);
        proposal.updateItem(itemId, itemTypes.requireActive(command.typeId()), command, userId);
        return toDetail(proposals.saveAndFlush(proposal));
    }

    /**
     * Removes an item of a Draft Proposal the caller is allowed to see, and returns the refreshed detail.
     *
     * @param proposalId the proposal id
     * @param itemId the item id
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws ProposalNotFoundException if the Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.sales.exception.ProposalNotEditableException if it is not a Draft
     * @throws com.fksoft.erp.domain.sales.exception.ProposalItemNotFoundException if the item is unknown
     */
    @Transactional
    public ProposalDetail removeItem(
            UUID proposalId, UUID itemId, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Proposal proposal = loadVisible(proposalId, userId, canSeeAll, canSeeUnassigned);
        proposal.removeItem(itemId, userId);
        return toDetail(proposals.saveAndFlush(proposal));
    }

    /**
     * Edits a Draft Proposal's commercial details (validity, terms, payment notes, Proposal-level discount)
     * the caller is allowed to see, and returns the refreshed detail (with the recomputed subtotal/total).
     * Creates no Financial, Receivable, Payment, Booking or Commission data.
     *
     * @param proposalId the proposal id
     * @param command the new commercial details
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws ProposalNotFoundException if the Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.sales.exception.ProposalNotEditableException if it is not a Draft
     * @throws com.fksoft.erp.domain.sales.exception.ProposalDiscountInvalidException if the discount is invalid
     */
    @Transactional
    public ProposalDetail updateDetails(
            UUID proposalId, UpdateProposalCommand command, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Proposal proposal = loadVisible(proposalId, userId, canSeeAll, canSeeUnassigned);
        proposal.updateCommercialDetails(command, userId);
        return toDetail(proposals.saveAndFlush(proposal));
    }

    /**
     * Submits a Draft Proposal the caller is allowed to see for review (Draft → READY_FOR_REVIEW). The
     * Proposal must have at least one item and a positive total. Creates no Sale, Order, Booking, Financial
     * or Commission data.
     *
     * @param proposalId the proposal id
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws ProposalNotFoundException if the Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.sales.exception.ProposalNotEditableException if it is not a Draft
     * @throws com.fksoft.erp.domain.sales.exception.ProposalHasNoItemsException if it has no items
     * @throws com.fksoft.erp.domain.sales.exception.ProposalTotalRequiredException if the total is not positive
     */
    @Transactional
    public ProposalDetail submitForReview(UUID proposalId, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Proposal proposal = loadVisible(proposalId, userId, canSeeAll, canSeeUnassigned);
        WorkflowState target;
        try {
            target = workflow.apply(
                    PROPOSAL_WORKFLOW, proposal.currentState(), "submit", WorkflowContext.of(proposal, userId));
        } catch (WorkflowTransitionNotAllowedException e) {
            throw new ProposalNotEditableException();
        }
        proposal.applySubmit(target, userId);
        return toDetail(proposals.saveAndFlush(proposal));
    }

    /**
     * Approves a Proposal under review the caller is allowed to see (Ready for Review → Approved), and returns
     * the refreshed detail. The action is gated by the {@code sales:proposal:approve} authority at the
     * delivery boundary. Creates no Sale, Order, Booking, Financial or Commission data.
     *
     * @param proposalId the proposal id
     * @param userId the approving user
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws ProposalNotFoundException if the Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.sales.exception.ProposalNotUnderReviewException if it is not Ready for Review
     */
    @Transactional
    public ProposalDetail approve(UUID proposalId, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Proposal proposal = loadVisible(proposalId, userId, canSeeAll, canSeeUnassigned);
        WorkflowState target;
        try {
            target = workflow.apply(
                    PROPOSAL_WORKFLOW, proposal.currentState(), "approve", WorkflowContext.of(proposal, userId));
        } catch (WorkflowTransitionNotAllowedException e) {
            throw new ProposalNotUnderReviewException();
        }
        proposal.applyApprove(target, userId);
        return toDetail(proposals.saveAndFlush(proposal));
    }

    /**
     * Rejects a Proposal under review the caller is allowed to see (Ready for Review → Rejected) with a
     * reason, and returns the refreshed detail. The action is gated by the {@code sales:proposal:approve}
     * authority at the delivery boundary. Does not send the Proposal to the client and creates no Sale,
     * Order, Booking, Financial or Commission data.
     *
     * @param proposalId the proposal id
     * @param reason the rejection reason (required)
     * @param note an optional free-text note
     * @param userId the approving user
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws ProposalNotFoundException if the Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.sales.exception.ProposalNotUnderReviewException if it is not Ready for Review
     * @throws com.fksoft.erp.domain.sales.exception.ProposalRejectionReasonRequiredException if no reason is given
     */
    @Transactional
    public ProposalDetail reject(
            UUID proposalId, UUID reasonId, String note, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Proposal proposal = loadVisible(proposalId, userId, canSeeAll, canSeeUnassigned);
        ProposalRejectionReason reason = rejectionReasons
                .findById(reasonId)
                .filter(ReferenceData::active)
                .orElseThrow(ProposalRejectionReasonNotAvailableException::new);
        WorkflowState target;
        try {
            target = workflow.apply(
                    PROPOSAL_WORKFLOW, proposal.currentState(), "reject", WorkflowContext.of(proposal, userId));
        } catch (WorkflowTransitionNotAllowedException e) {
            throw new ProposalNotUnderReviewException();
        }
        proposal.applyReject(target, userId, reason, note);
        return toDetail(proposals.saveAndFlush(proposal));
    }

    /**
     * Marks an approved Proposal the caller is allowed to see as sent to the client (Approved → Sent), with an
     * optional descriptive sending channel, and returns the refreshed detail. The action is gated by the
     * {@code sales:proposal:update} authority at the delivery boundary. Does NOT trigger any real e-mail/
     * WhatsApp/phone integration, and creates no customer acceptance, Commercial Order, Booking, Financial or
     * Commission data; the Proposal stays open for the client's decision.
     *
     * @param proposalId the proposal id
     * @param channel the descriptive sending channel, or {@code null} (the channel is optional)
     * @param userId the user registering the send
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws ProposalNotFoundException if the Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.sales.exception.ProposalNotApprovedException if it is not Approved
     */
    @Transactional
    public ProposalDetail markAsSent(
            UUID proposalId, UUID channelId, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Proposal proposal = loadVisible(proposalId, userId, canSeeAll, canSeeUnassigned);
        // The sending channel is optional; when given, it must reference an active cadastro value.
        SendingChannel channel = channelId == null
                ? null
                : sendingChannels
                        .findById(channelId)
                        .filter(ReferenceData::active)
                        .orElseThrow(SendingChannelNotAvailableException::new);
        WorkflowState target;
        try {
            target = workflow.apply(
                    PROPOSAL_WORKFLOW, proposal.currentState(), "send", WorkflowContext.of(proposal, userId));
        } catch (WorkflowTransitionNotAllowedException e) {
            throw new ProposalNotApprovedException();
        }
        proposal.applySend(target, userId, channel);
        return toDetail(proposals.saveAndFlush(proposal));
    }

    /**
     * Registers that the client accepted a sent Proposal the caller is allowed to see (Sent → Accepted), with
     * an optional confirmation note, and returns the refreshed detail. Gated by the
     * {@code sales:proposal:update} authority at the delivery boundary. Creates no Booking, Financial,
     * Commission or Commercial Order data.
     *
     * @param proposalId the proposal id
     * @param note an optional client confirmation note
     * @param userId the user registering the acceptance
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws ProposalNotFoundException if the Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.sales.exception.ProposalNotSentException if it is not Sent
     */
    @Transactional
    public ProposalDetail acceptByCustomer(
            UUID proposalId, String note, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Proposal proposal = loadVisible(proposalId, userId, canSeeAll, canSeeUnassigned);
        WorkflowState target;
        try {
            target = workflow.apply(
                    PROPOSAL_WORKFLOW, proposal.currentState(), "accept", WorkflowContext.of(proposal, userId));
        } catch (WorkflowTransitionNotAllowedException e) {
            throw new ProposalNotSentException();
        }
        proposal.applyAccept(target, userId, note);
        return toDetail(proposals.saveAndFlush(proposal));
    }

    /**
     * Registers that the client rejected a sent Proposal the caller is allowed to see (Sent → Rejected) with a
     * reason, and returns the refreshed detail. Gated by the {@code sales:proposal:update} authority at the
     * delivery boundary. The rejected Proposal is terminal (it frees the Opportunity for a new Proposal) and
     * creates no Booking, Financial, Commission or Commercial Order data.
     *
     * @param proposalId the proposal id
     * @param reason the customer-rejection reason (required)
     * @param note an optional free-text note
     * @param userId the user registering the rejection
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws ProposalNotFoundException if the Proposal does not exist
     * @throws ProposalAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.sales.exception.ProposalNotSentException if it is not Sent
     * @throws com.fksoft.erp.domain.sales.exception.ProposalRejectionReasonRequiredException if no reason is given
     */
    @Transactional
    public ProposalDetail declineByCustomer(
            UUID proposalId, UUID reasonId, String note, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Proposal proposal = loadVisible(proposalId, userId, canSeeAll, canSeeUnassigned);
        CustomerRejectionReason reason = customerRejectionReasons
                .findById(reasonId)
                .filter(ReferenceData::active)
                .orElseThrow(CustomerRejectionReasonNotAvailableException::new);
        WorkflowState target;
        try {
            target = workflow.apply(
                    PROPOSAL_WORKFLOW, proposal.currentState(), "decline", WorkflowContext.of(proposal, userId));
        } catch (WorkflowTransitionNotAllowedException e) {
            throw new ProposalNotSentException();
        }
        proposal.applyDecline(target, userId, reason, note);
        return toDetail(proposals.saveAndFlush(proposal));
    }

    /**
     * Operational, paginated Proposal list, filtered by the given criteria and restricted to the caller's
     * visibility. Terminal-negative Proposals (REJECTED/EXPIRED/CANCELLED) are excluded unless the status
     * filter explicitly includes them. Enriches each item with the responsible's and the source
     * Opportunity's resolved names. Exposes commercial-offer data only.
     *
     * @param criteria the optional filters and search term
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return a page of Proposal list items
     */
    @Transactional(readOnly = true)
    public Page<ProposalListItem> list(
            ProposalSearchCriteria criteria,
            Pageable pageable,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        Specification<Proposal> spec = ProposalSpecifications.matching(criteria)
                .and(accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned));
        Page<Proposal> page = proposals.findAll(spec, pageable);
        Map<UUID, String> names = resolveNames(page.getContent().stream().map(Proposal::responsiblePersonId));
        Map<UUID, String> opportunityNames =
                resolveOpportunityNames(page.getContent().stream().map(Proposal::opportunityId));
        return page.map(p -> ProposalListItem.from(
                p, nameOf(names, p.responsiblePersonId()), opportunityNames.get(p.opportunityId())));
    }

    /**
     * Minimum Proposal-flow indicators over the Proposals visible to the caller. The volume figures (total,
     * by status, by responsible, proposed amount, accepted amount, rejected count) cover the requested
     * period (by creation date); the operational figures (waiting for review, waiting for customer
     * decision) are a current snapshot of all the visible Proposals. Read-only; never exposes Sale, Sales
     * Order, Booking, Financial, Payment or Commission data.
     *
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return the indicators
     */
    @Transactional(readOnly = true)
    public ProposalIndicators indicators(
            UUID userId, boolean canSeeAll, boolean canSeeUnassigned, Instant from, Instant to) {
        Specification<Proposal> visible = accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned);

        // Volume — over the period.
        Map<String, Long> countByStatus = indicatorQueries.countByStatus(visible, from, to);
        long total = countByStatus.values().stream().mapToLong(Long::longValue).sum();
        long rejectedCount = countByStatus.getOrDefault("REJECTED", 0L);
        Map<String, BigDecimal> sumByStatus = indicatorQueries.sumTotalByStatus(visible, from, to);
        BigDecimal proposedAmount = sumByStatus.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal acceptedAmount = sumByStatus.getOrDefault("ACCEPTED", BigDecimal.ZERO);
        Map<UUID, Long> byResponsibleId = indicatorQueries.countByResponsible(visible, from, to);

        // Operational — current snapshot (no period).
        Map<String, Long> countByStatusNow = indicatorQueries.countByStatus(visible, null, null);
        long waitingForReview = countByStatusNow.getOrDefault("READY_FOR_REVIEW", 0L);
        long waitingForCustomerDecision = countByStatusNow.getOrDefault("SENT", 0L);

        Map<UUID, String> names = resolveNames(byResponsibleId.keySet().stream());
        List<ProposalIndicators.StatusCount> statusCounts = countByStatus.entrySet().stream()
                .map(e -> new ProposalIndicators.StatusCount(e.getKey(), e.getValue()))
                .toList();
        List<ProposalIndicators.ResponsibleCount> responsibleCounts = byResponsibleId.entrySet().stream()
                .map(e -> new ProposalIndicators.ResponsibleCount(nameOf(names, e.getKey()), e.getValue()))
                .toList();

        return new ProposalIndicators(
                total,
                statusCounts,
                responsibleCounts,
                proposedAmount,
                acceptedAmount,
                rejectedCount,
                waitingForReview,
                waitingForCustomerDecision);
    }

    private ProposalDetail toDetail(Proposal proposal) {
        Opportunity opportunity =
                opportunities.findById(proposal.opportunityId()).orElseThrow(OpportunityNotFoundException::new);
        Lead lead = leads.findById(proposal.leadId()).orElseThrow(LeadNotFoundException::new);
        Map<UUID, String> names = resolveNames(Stream.concat(
                Stream.of(proposal.responsiblePersonId()),
                proposal.statusChanges().stream().map(ProposalStatusChange::changedBy)));
        UUID commercialOrderId = orders.findFirstByProposalIdAndStatusIn(proposal.id(), ACTIVE_ORDER_STATUSES)
                .map(CommercialOrder::id)
                .orElse(null);
        return ProposalDetail.from(proposal, opportunity, lead, names, commercialOrderId);
    }

    private Proposal loadVisible(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Proposal proposal = proposals.findById(id).orElseThrow(ProposalNotFoundException::new);
        if (!accessPolicy.canSee(proposal, userId, canSeeAll, canSeeUnassigned)) {
            throw new ProposalAccessDeniedException();
        }
        return proposal;
    }

    private Map<UUID, String> resolveNames(Stream<UUID> ids) {
        Set<UUID> set = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : users.findAllById(set).stream().collect(Collectors.toMap(User::id, User::username));
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
