package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.exception.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.exception.LeadNotQualifiedForOpportunityException;
import com.fksoft.erp.domain.crm.exception.OpportunityAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.OpportunityAlreadyExistsForLeadException;
import com.fksoft.erp.domain.crm.exception.OpportunityNotFoundException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityActivity;
import com.fksoft.erp.domain.crm.model.OpportunityCreated;
import com.fksoft.erp.domain.crm.model.OpportunityLossReason;
import com.fksoft.erp.domain.crm.model.OpportunityPendingReasons;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.model.OpportunityStageChange;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityIndicatorQueries;
import com.fksoft.erp.domain.crm.repository.OpportunityLastActivityRow;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.service.data.CreateOpportunityCommand;
import com.fksoft.erp.domain.crm.service.data.OpportunityDetail;
import com.fksoft.erp.domain.crm.service.data.OpportunityIndicators;
import com.fksoft.erp.domain.crm.service.data.OpportunityListItem;
import com.fksoft.erp.domain.crm.service.data.OpportunitySearchCriteria;
import com.fksoft.erp.domain.crm.service.data.PendingOpportunity;
import com.fksoft.erp.domain.crm.service.data.RecordActivityCommand;
import com.fksoft.erp.domain.crm.service.data.UpdateOpportunityDetailsCommand;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private final LeadAccessPolicy leadAccessPolicy;
    private final OpportunityAccessPolicy accessPolicy;
    private final OpportunityIndicatorQueries indicatorQueries;
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

        List<UUID> opportunityIds =
                page.getContent().stream().map(Opportunity::id).toList();
        Map<UUID, Instant> lastActivity = opportunityIds.isEmpty()
                ? Map.of()
                : opportunities.findLastActivityAt(opportunityIds).stream()
                        .collect(Collectors.toMap(
                                OpportunityLastActivityRow::getOpportunityId,
                                OpportunityLastActivityRow::getLastActivityAt));

        Set<UUID> responsibleIds = page.getContent().stream()
                .map(Opportunity::responsiblePersonId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = responsibleIds.isEmpty()
                ? Map.of()
                : users.findAllById(responsibleIds).stream().collect(Collectors.toMap(User::id, User::username));

        return page.map(opportunity -> OpportunityListItem.from(
                opportunity, nameOf(names, opportunity.responsiblePersonId()), lastActivity.get(opportunity.id())));
    }

    /**
     * Operational pending-items worklist of the Opportunities visible to the caller that need action
     * (no recent activity, overdue next action, stuck in NEW/DISCOVERY, ready for a proposal, or past
     * the expected closing date), each with its reasons. Read-only; creates no Proposal, Sale, Booking,
     * Financial record or notification, and never modifies the Opportunity or its source Lead. LOST
     * Opportunities are excluded.
     *
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Opportunity
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return a page of pending Opportunity items
     */
    @Transactional(readOnly = true)
    public Page<PendingOpportunity> pending(
            Pageable pageable, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Specification<Opportunity> spec = OpportunityPendingSpecifications.pending(now, today)
                .and(accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned));
        Page<Opportunity> page = opportunities.findAll(spec, pageable);

        List<UUID> opportunityIds =
                page.getContent().stream().map(Opportunity::id).toList();
        Map<UUID, Instant> lastActivity = opportunityIds.isEmpty()
                ? Map.of()
                : opportunities.findLastActivityAt(opportunityIds).stream()
                        .collect(Collectors.toMap(
                                OpportunityLastActivityRow::getOpportunityId,
                                OpportunityLastActivityRow::getLastActivityAt));

        Set<UUID> responsibleIds = page.getContent().stream()
                .map(Opportunity::responsiblePersonId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = responsibleIds.isEmpty()
                ? Map.of()
                : users.findAllById(responsibleIds).stream().collect(Collectors.toMap(User::id, User::username));

        return page.map(opportunity -> {
            Instant lastActivityAt = lastActivity.get(opportunity.id());
            return PendingOpportunity.from(
                    opportunity,
                    nameOf(names, opportunity.responsiblePersonId()),
                    lastActivityAt,
                    OpportunityPendingReasons.of(opportunity, now, today, lastActivityAt));
        });
    }

    /**
     * Minimum commercial-pipeline indicators over the Opportunities visible to the caller. The volume
     * figures (total, lost, by stage/origin/responsible) cover the requested period (by creation date);
     * the pipeline figures (active, ready for proposal, overdue close, active value, value by
     * responsible) are a current snapshot of all the visible Opportunities. Read-only; never exposes
     * Proposal, Sale, Booking, Financial or Commission data.
     *
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Opportunity
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return the indicators
     */
    @Transactional(readOnly = true)
    public OpportunityIndicators indicators(
            UUID userId, boolean canSeeAll, boolean canSeeUnassigned, Instant from, Instant to) {
        Specification<Opportunity> visible = accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // Volume — over the period.
        Map<OpportunityStage, Long> byStagePeriod = indicatorQueries.countByStage(visible, from, to);
        long total = byStagePeriod.values().stream().mapToLong(Long::longValue).sum();
        long lost = byStagePeriod.getOrDefault(OpportunityStage.LOST, 0L);
        Map<String, Long> byOrigin = indicatorQueries.countByOrigin(visible, from, to);
        Map<UUID, Long> byResponsibleId = indicatorQueries.countByResponsible(visible, from, to);

        // Pipeline — current snapshot (no period).
        Map<OpportunityStage, Long> byStageNow = indicatorQueries.countByStage(visible, null, null);
        long active = byStageNow.entrySet().stream()
                .filter(e -> !e.getKey().isTerminal())
                .mapToLong(Map.Entry::getValue)
                .sum();
        long readyForProposal = byStageNow.getOrDefault(OpportunityStage.READY_FOR_PROPOSAL, 0L);
        BigDecimal activePipelineValue = indicatorQueries.sumActivePipelineValue(visible);
        Map<UUID, BigDecimal> valueByResponsibleId = indicatorQueries.sumActiveValueByResponsible(visible);
        long overdueClose = indicatorQueries.countActiveOverdueClose(visible, today);

        Map<UUID, String> names =
                resolveNames(Stream.concat(byResponsibleId.keySet().stream(), valueByResponsibleId.keySet().stream()));

        List<OpportunityIndicators.StageCount> stageCounts = byStagePeriod.entrySet().stream()
                .map(e -> new OpportunityIndicators.StageCount(e.getKey(), e.getValue()))
                .toList();
        List<OpportunityIndicators.OriginCount> originCounts = byOrigin.entrySet().stream()
                .map(e -> new OpportunityIndicators.OriginCount(e.getKey(), e.getValue()))
                .toList();
        List<OpportunityIndicators.ResponsibleCount> responsibleCounts = byResponsibleId.entrySet().stream()
                .map(e -> new OpportunityIndicators.ResponsibleCount(nameOf(names, e.getKey()), e.getValue()))
                .toList();
        List<OpportunityIndicators.ResponsibleValue> responsibleValues = valueByResponsibleId.entrySet().stream()
                .map(e -> new OpportunityIndicators.ResponsibleValue(nameOf(names, e.getKey()), e.getValue()))
                .toList();

        return new OpportunityIndicators(
                total,
                lost,
                stageCounts,
                originCounts,
                responsibleCounts,
                active,
                readyForProposal,
                overdueClose,
                activePipelineValue,
                responsibleValues);
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
     * @param reason the loss reason
     * @param note optional loss note
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Opportunity
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws OpportunityNotFoundException if the Opportunity does not exist
     * @throws OpportunityAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.crm.exception.OpportunityCannotBeMarkedLostException if already lost
     */
    @Transactional
    public OpportunityDetail markLost(
            UUID id,
            OpportunityLossReason reason,
            String note,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        Opportunity opportunity = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        opportunity.markLost(reason, userId, note);
        return toDetail(opportunities.saveAndFlush(opportunity));
    }

    /**
     * Moves an Opportunity the caller is allowed to see to another active pipeline stage and returns the
     * refreshed detail. Movement among the active stages is free; LOST is reached only through
     * {@link #markLost} (it is terminal).
     *
     * @param id the opportunity id
     * @param target the destination stage
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Opportunity
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws OpportunityNotFoundException if the Opportunity does not exist
     * @throws OpportunityAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.crm.exception.OpportunityStageTransitionException if the transition is
     *     not allowed (from LOST, to LOST, or to the current stage)
     */
    @Transactional
    public OpportunityDetail changeStage(
            UUID id, OpportunityStage target, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Opportunity opportunity = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        opportunity.moveToStage(target, userId);
        return toDetail(opportunities.saveAndFlush(opportunity));
    }

    /**
     * Registers a commercial activity on an Opportunity the caller is allowed to see, and returns the
     * refreshed detail. The activity is append-only history; it never moves the stage.
     *
     * @param id the opportunity id
     * @param command the activity data (type, result, description, date, optional next action)
     * @param userId the acting user (the activity's author)
     * @param canSeeAll whether the caller may see every Opportunity
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws OpportunityNotFoundException if the Opportunity does not exist
     * @throws OpportunityAccessDeniedException if the caller may not see it
     */
    @Transactional
    public OpportunityDetail recordActivity(
            UUID id, RecordActivityCommand command, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Opportunity opportunity = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        opportunity.recordActivity(
                command.type(),
                command.result(),
                command.description(),
                command.occurredAt(),
                command.nextActionDate(),
                userId);
        return toDetail(opportunities.saveAndFlush(opportunity));
    }

    /**
     * Edits the commercial details (estimated value, expected closing date, product type, notes) of an
     * Opportunity the caller is allowed to see, and returns the refreshed detail. The main interest, stage
     * and source Lead are not affected.
     *
     * @param id the opportunity id
     * @param command the new commercial details ({@code null} clears a field)
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Opportunity
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws OpportunityNotFoundException if the Opportunity does not exist
     * @throws OpportunityAccessDeniedException if the caller may not see it
     */
    @Transactional
    public OpportunityDetail updateDetails(
            UUID id,
            UpdateOpportunityDetailsCommand command,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        Opportunity opportunity = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        opportunity.updateCommercialDetails(
                command.estimatedValue(), command.expectedCloseDate(), command.productType(), command.notes(), userId);
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
        Set<UUID> actorIds = Stream.of(
                        Stream.of(opportunity.responsiblePersonId(), opportunity.lostBy()),
                        opportunity.stageChanges().stream().map(OpportunityStageChange::changedBy),
                        opportunity.activities().stream().map(OpportunityActivity::registeredBy))
                .flatMap(s -> s)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = actorIds.isEmpty()
                ? Map.of()
                : users.findAllById(actorIds).stream().collect(Collectors.toMap(User::id, User::username));
        return OpportunityDetail.from(opportunity, lead, names);
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
