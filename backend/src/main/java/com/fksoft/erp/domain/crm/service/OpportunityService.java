package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.exception.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.exception.LeadNotQualifiedForOpportunityException;
import com.fksoft.erp.domain.crm.exception.LossReasonNotAvailableException;
import com.fksoft.erp.domain.crm.exception.OpportunityAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.OpportunityAlreadyExistsForLeadException;
import com.fksoft.erp.domain.crm.exception.OpportunityNotFoundException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.LossReason;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityCreated;
import com.fksoft.erp.domain.crm.model.ReferenceData;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.LossReasonRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.service.data.CreateOpportunityCommand;
import com.fksoft.erp.domain.crm.service.data.OpportunityDetail;
import com.fksoft.erp.domain.crm.service.data.OpportunityListItem;
import com.fksoft.erp.domain.crm.service.data.OpportunitySearchCriteria;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
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
 * Application Service for commercial Opportunities (Commercial / CRM): creates an Opportunity from a
 * QUALIFIED Lead and serves the operational list. One service per area handles both command and reads.
 * Never creates a Proposal, Customer, Sale, Sales Order, Booking, Financial record or Commission, and
 * never modifies the source Lead.
 */
@Service
@RequiredArgsConstructor
public class OpportunityService {

    private final OpportunityRepository opportunities;
    private final LeadRepository leads;
    private final UserRepository users;
    private final LossReasonRepository lossReasons;
    private final LeadAccessPolicy leadAccessPolicy;
    private final OpportunityAccessPolicy accessPolicy;
    private final ApplicationEventPublisher events;

    /**
     * Creates an Opportunity from a Qualified Lead the caller is allowed to see. A Lead originates at
     * most one Opportunity. The responsible defaults to the lead's; an explicit one (when active)
     * overrides it.
     *
     * @param command the opportunity data (source lead + optional commercial estimates)
     * @param createdBy id of the authenticated user
     * @param canSeeAllLeads whether the caller may see every lead
     * @param canSeeUnassignedLeads whether the caller may also see the unassigned pool
     * @return the new opportunity id
     * @throws LeadNotFoundException if the source lead does not exist
     * @throws LeadAccessDeniedException if the caller may not see the source lead
     * @throws LeadNotQualifiedForOpportunityException if the lead is not QUALIFIED
     * @throws OpportunityAlreadyExistsForLeadException if the lead already originated an opportunity
     * @throws ResponsiblePersonNotFoundException if a responsible is given but unknown/inactive
     */
    @Transactional
    public UUID create(
            CreateOpportunityCommand command, UUID createdBy, boolean canSeeAllLeads, boolean canSeeUnassignedLeads) {
        Lead lead = leads.findById(command.leadId()).orElseThrow(LeadNotFoundException::new);
        if (!leadAccessPolicy.canSee(lead, createdBy, canSeeAllLeads, canSeeUnassignedLeads)) {
            throw new LeadAccessDeniedException();
        }
        if (lead.status() != LeadStatus.QUALIFIED) {
            throw new LeadNotQualifiedForOpportunityException();
        }
        opportunities.findByLeadId(lead.id()).ifPresent(existing -> {
            throw new OpportunityAlreadyExistsForLeadException(existing.id());
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
            // The lead's responsible is preserved by default (already validated when assigned).
            responsibleId = lead.responsiblePersonId();
        }
        Opportunity opportunity = Opportunity.createFromLead(lead, responsibleId, command, createdBy);
        opportunities.save(opportunity);
        events.publishEvent(new OpportunityCreated(opportunity.id(), lead.id(), createdBy, responsibleId));
        return opportunity.id();
    }

    /**
     * Operational, paginated Opportunity list filtered by the criteria and the caller's visibility.
     * Lost Opportunities are excluded unless the criteria explicitly include LOST.
     *
     * @param criteria the (optional) filters
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Opportunity
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return a page of operational Opportunity items
     */
    @Transactional(readOnly = true)
    public Page<OpportunityListItem> list(
            OpportunitySearchCriteria criteria,
            Pageable pageable,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        Specification<Opportunity> spec = OpportunitySpecifications.matching(criteria)
                .and(accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned));
        Page<Opportunity> page = opportunities.findAll(spec, pageable);

        Set<UUID> responsibleIds = page.getContent().stream()
                .map(Opportunity::responsiblePersonId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = responsibleIds.isEmpty()
                ? Map.of()
                : users.findAllById(responsibleIds).stream().collect(Collectors.toMap(User::id, User::username));

        return page.map(
                opportunity -> OpportunityListItem.from(opportunity, nameOf(names, opportunity.responsiblePersonId())));
    }

    /**
     * Full detail of an Opportunity the caller is allowed to see, with the source Lead kept traceable.
     *
     * @param id the opportunity id
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Opportunity
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the detail read model
     * @throws OpportunityNotFoundException if the Opportunity does not exist
     * @throws OpportunityAccessDeniedException if the caller may not see it
     */
    @Transactional(readOnly = true)
    public OpportunityDetail detail(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        return toDetail(loadVisible(id, userId, canSeeAll, canSeeUnassigned));
    }

    /**
     * Marks an Opportunity the caller is allowed to see as lost with a reason, and returns the refreshed
     * detail. The source Lead is not affected.
     *
     * @param id the opportunity id
     * @param lossReasonId the (active) loss reason id
     * @param note optional loss note
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Opportunity
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws OpportunityNotFoundException if the Opportunity does not exist
     * @throws OpportunityAccessDeniedException if the caller may not see it
     * @throws LossReasonNotAvailableException if the loss reason is unknown or inactive
     * @throws com.fksoft.erp.domain.crm.exception.OpportunityCannotBeMarkedLostException if already lost
     */
    @Transactional
    public OpportunityDetail markLost(
            UUID id, UUID lossReasonId, String note, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Opportunity opportunity = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        LossReason reason = lossReasons
                .findById(lossReasonId)
                .filter(ReferenceData::active)
                .orElseThrow(LossReasonNotAvailableException::new);
        opportunity.markLost(reason, userId, note);
        return toDetail(opportunities.saveAndFlush(opportunity));
    }

    private Opportunity loadVisible(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Opportunity opportunity = opportunities.findById(id).orElseThrow(OpportunityNotFoundException::new);
        if (!accessPolicy.canSee(opportunity, userId, canSeeAll, canSeeUnassigned)) {
            throw new OpportunityAccessDeniedException();
        }
        return opportunity;
    }

    private OpportunityDetail toDetail(Opportunity opportunity) {
        Lead lead = leads.findById(opportunity.leadId()).orElseThrow(LeadNotFoundException::new);
        Set<UUID> actorIds = Stream.of(opportunity.responsiblePersonId(), opportunity.lostBy())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = actorIds.isEmpty()
                ? Map.of()
                : users.findAllById(actorIds).stream().collect(Collectors.toMap(User::id, User::username));
        return OpportunityDetail.from(
                opportunity,
                lead,
                nameOf(names, opportunity.responsiblePersonId()),
                nameOf(names, opportunity.lostBy()));
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }
}
