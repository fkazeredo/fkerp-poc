package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.crm.exception.OpportunityAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.OpportunityNotFoundException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.service.OpportunityAccessPolicy;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.sales.exception.OpportunityNotReadyForProposalException;
import com.fksoft.erp.domain.sales.exception.ProposalAccessDeniedException;
import com.fksoft.erp.domain.sales.exception.ProposalAlreadyExistsForOpportunityException;
import com.fksoft.erp.domain.sales.exception.ProposalNotFoundException;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalCreated;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalDetail;
import com.fksoft.erp.domain.sales.service.data.ProposalListItem;
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
    private static final Set<ProposalStatus> OPEN_STATUSES =
            Stream.of(ProposalStatus.values()).filter(ProposalStatus::isOpen).collect(Collectors.toUnmodifiableSet());

    private final ProposalRepository proposals;
    private final ProposalAccessPolicy accessPolicy;
    private final OpportunityRepository opportunities;
    private final OpportunityAccessPolicy opportunityAccessPolicy;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

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
        if (opportunity.stage() != OpportunityStage.READY_FOR_PROPOSAL) {
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
        Proposal proposal = Proposal.createFromOpportunity(opportunity, responsibleId, command, createdBy);
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
        Proposal proposal = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        Opportunity opportunity =
                opportunities.findById(proposal.opportunityId()).orElseThrow(OpportunityNotFoundException::new);
        Map<UUID, String> names = resolveNames(Stream.of(proposal.responsiblePersonId()));
        return ProposalDetail.from(proposal, opportunity, names);
    }

    /**
     * Operational, paginated Proposal list filtered by the caller's visibility.
     *
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Proposal
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return a page of Proposal list items
     */
    @Transactional(readOnly = true)
    public Page<ProposalListItem> list(Pageable pageable, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Specification<Proposal> spec = accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned);
        Page<Proposal> page = proposals.findAll(spec, pageable);
        Map<UUID, String> names = resolveNames(page.getContent().stream().map(Proposal::responsiblePersonId));
        return page.map(p -> ProposalListItem.from(p, nameOf(names, p.responsiblePersonId())));
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

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }
}
