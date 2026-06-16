package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.exception.DuplicateLeadException;
import com.fksoft.erp.domain.crm.exception.InteractionResultNotAvailableException;
import com.fksoft.erp.domain.crm.exception.InteractionTypeNotAvailableException;
import com.fksoft.erp.domain.crm.exception.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.LeadAssignmentNotAllowedException;
import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.exception.LossReasonNotAvailableException;
import com.fksoft.erp.domain.crm.exception.OriginNotAvailableException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.crm.model.InteractionResult;
import com.fksoft.erp.domain.crm.model.InteractionType;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadRegistered;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.LossReason;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.model.PendingLeadReasons;
import com.fksoft.erp.domain.crm.model.ReferenceData;
import com.fksoft.erp.domain.crm.repository.InteractionResultRepository;
import com.fksoft.erp.domain.crm.repository.InteractionTypeRepository;
import com.fksoft.erp.domain.crm.repository.LatestInteractionRow;
import com.fksoft.erp.domain.crm.repository.LeadIndicatorQueries;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.LossReasonRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.crm.service.data.LeadDetail;
import com.fksoft.erp.domain.crm.service.data.LeadIndicators;
import com.fksoft.erp.domain.crm.service.data.LeadListItem;
import com.fksoft.erp.domain.crm.service.data.LeadSearchCriteria;
import com.fksoft.erp.domain.crm.service.data.PendingLead;
import com.fksoft.erp.domain.crm.service.data.RecordInteractionCommand;
import com.fksoft.erp.domain.crm.service.data.RegisterLeadCommand;
import com.fksoft.erp.domain.crm.service.data.Responsible;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
 * Application Service of the Commercial / CRM module: registers Leads, serves the operational list,
 * pending worklist, indicators and the Lead detail with its commercial history, and applies the
 * transitions that change a Lead (qualify, mark lost, reassign, record interaction). One service per
 * area handles both commands and reads. It never creates Customer, Opportunity, Sale, Sales Order,
 * Booking or Financial data, and resolves cross-aggregate data (responsible/actor names) from Identity.
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leads;
    private final OriginRepository origins;
    private final InteractionTypeRepository interactionTypes;
    private final InteractionResultRepository interactionResults;
    private final LossReasonRepository lossReasons;
    private final UserRepository users;
    private final LeadAccessPolicy accessPolicy;
    private final LeadAssignmentPolicy assignmentPolicy;
    private final LeadIndicatorQueries indicatorQueries;
    private final ApplicationEventPublisher events;

    /**
     * Registers a new Lead (status NEW), optionally recording an initial note as the first
     * interaction, and publishes {@link LeadRegistered}.
     *
     * @param command the lead data (already validated at the boundary)
     * @param createdBy id of the authenticated user creating the lead
     * @return the new lead id
     * @throws OriginNotAvailableException if the origin is unknown or inactive
     * @throws ResponsiblePersonNotFoundException if a responsible person is given but unknown/inactive
     */
    @Transactional
    public UUID register(RegisterLeadCommand command, UUID createdBy) {
        Origin origin = origins.findById(command.originId())
                .filter(ReferenceData::active)
                .orElseThrow(OriginNotAvailableException::new);
        if (command.responsiblePersonId() != null
                && users.findById(command.responsiblePersonId())
                        .filter(User::active)
                        .isEmpty()) {
            throw new ResponsiblePersonNotFoundException();
        }
        rejectDuplicate(command);
        Lead lead = Lead.register(command, origin, createdBy);
        if (command.initialNote() != null && !command.initialNote().isBlank()) {
            InteractionType noteType = interactionTypes
                    .findByCode(InteractionType.INTERNAL_NOTE_CODE)
                    .orElseThrow(() -> new IllegalStateException("Missing INTERNAL_NOTE interaction type"));
            lead.addInitialNote(noteType, command.initialNote(), createdBy);
        }
        leads.save(lead);
        events.publishEvent(new LeadRegistered(lead.id(), origin.id(), createdBy, lead.responsiblePersonId()));
        return lead.id();
    }

    /**
     * Rejects a new Lead that duplicates an OPEN (non-lost) Lead by phone/WhatsApp number or e-mail.
     * Numbers are matched across both fields (the same number is often reused); the e-mail is matched
     * case-insensitively. A Lost Lead never blocks. The existing Lead id is carried on the error.
     *
     * @param command the new lead data
     * @throws DuplicateLeadException if an open Lead already shares the phone/WhatsApp or e-mail
     */
    private void rejectDuplicate(RegisterLeadCommand command) {
        String phone = blankOrNull(command.phone());
        String whatsapp = blankOrNull(command.whatsapp());
        String email = blankOrNull(command.email());
        String normalizedEmail = email == null ? null : email.toLowerCase(Locale.ROOT);
        leads.findOpenDuplicates(phone, whatsapp, normalizedEmail).stream()
                .findFirst()
                .ifPresent(existing -> {
                    throw new DuplicateLeadException(existing.id());
                });
    }

    private static String blankOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Operational, paginated Lead list filtered by the criteria and the caller's visibility.
     *
     * @param criteria the (optional) filters
     * @param pageable page, size and sort
     * @param currentUserId the calling user
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return a page of operational Lead items
     */
    @Transactional(readOnly = true)
    public Page<LeadListItem> list(
            LeadSearchCriteria criteria,
            Pageable pageable,
            UUID currentUserId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        Specification<Lead> spec = LeadSpecifications.matching(criteria)
                .and(accessPolicy.visibleTo(currentUserId, canSeeAll, canSeeUnassigned));
        Page<Lead> page = leads.findAll(spec, pageable);

        List<UUID> leadIds = page.getContent().stream().map(Lead::id).toList();
        Map<UUID, LatestInteractionRow> latestByLead = leadIds.isEmpty()
                ? Map.of()
                : leads.findLatestInteractions(leadIds).stream()
                        .collect(Collectors.toMap(LatestInteractionRow::getLeadId, row -> row, (a, b) -> a));
        Map<UUID, String> names = resolveNames(page.getContent().stream().map(Lead::responsiblePersonId));

        return page.map(lead ->
                LeadListItem.from(lead, nameOf(names, lead.responsiblePersonId()), latestByLead.get(lead.id())));
    }

    /**
     * Operational pending-items worklist visible to the caller, each with its reasons.
     *
     * @param pageable page, size and sort
     * @param currentUserId the calling user
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return a page of pending Leads
     */
    @Transactional(readOnly = true)
    public Page<PendingLead> pending(
            Pageable pageable, UUID currentUserId, boolean canSeeAll, boolean canSeeUnassigned) {
        Instant now = Instant.now();
        Specification<Lead> spec = LeadPendingSpecifications.pending(now)
                .and(accessPolicy.visibleTo(currentUserId, canSeeAll, canSeeUnassigned));
        Page<Lead> page = leads.findAll(spec, pageable);

        List<UUID> leadIds = page.getContent().stream().map(Lead::id).toList();
        Set<UUID> withInteractions = leadIds.isEmpty()
                ? Set.of()
                : leads.findLatestInteractions(leadIds).stream()
                        .map(LatestInteractionRow::getLeadId)
                        .collect(Collectors.toSet());
        Map<UUID, String> names = resolveNames(page.getContent().stream().map(Lead::responsiblePersonId));

        return page.map(lead -> PendingLead.from(
                lead,
                nameOf(names, lead.responsiblePersonId()),
                PendingLeadReasons.of(lead, now, withInteractions.contains(lead.id()))));
    }

    /**
     * Minimum top-of-funnel indicators over the Leads visible to the caller, in an optional period.
     *
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return the indicators
     */
    @Transactional(readOnly = true)
    public LeadIndicators indicators(
            UUID userId, boolean canSeeAll, boolean canSeeUnassigned, Instant from, Instant to) {
        Specification<Lead> visible = accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned);

        Map<LeadStatus, Long> byStatus = indicatorQueries.countByStatus(visible, from, to);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Long> byOrigin = indicatorQueries.countByOrigin(visible, from, to);
        Map<UUID, Long> byResponsibleId = indicatorQueries.countByResponsible(visible, from, to);
        Map<UUID, String> names = resolveNames(byResponsibleId.keySet().stream());

        List<LeadIndicators.OriginCount> originCounts = byOrigin.entrySet().stream()
                .map(e -> new LeadIndicators.OriginCount(e.getKey(), e.getValue()))
                .toList();
        List<LeadIndicators.ResponsibleCount> responsibleCounts = byResponsibleId.entrySet().stream()
                .map(e -> new LeadIndicators.ResponsibleCount(nameOf(names, e.getKey()), e.getValue()))
                .toList();

        return new LeadIndicators(
                total,
                byStatus.getOrDefault(LeadStatus.NEW, 0L),
                byStatus.getOrDefault(LeadStatus.CONTACTED, 0L),
                byStatus.getOrDefault(LeadStatus.QUALIFIED, 0L),
                byStatus.getOrDefault(LeadStatus.LOST, 0L),
                indicatorQueries.countWaitingFirstContact(visible, from, to),
                originCounts,
                responsibleCounts);
    }

    /**
     * Active users that can be assigned as a Lead responsible (assignment selector + responsible filter).
     *
     * @return active users as lightweight items, ordered by username
     */
    @Transactional(readOnly = true)
    public List<Responsible> listResponsibles() {
        return users.findByActiveTrueOrderByUsernameAsc().stream()
                .map(u -> new Responsible(u.id(), u.username()))
                .toList();
    }

    /**
     * Full detail of a Lead the caller is allowed to see (its commercial history included).
     *
     * @param id the lead id
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the lead detail
     * @throws LeadNotFoundException if no lead has the given id
     * @throws LeadAccessDeniedException if the lead exists but is not visible to the caller
     */
    @Transactional(readOnly = true)
    public LeadDetail detail(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        return toDetail(loadVisible(id, userId, canSeeAll, canSeeUnassigned));
    }

    /**
     * Qualifies a Lead with its main commercial interest and returns the refreshed detail.
     *
     * @param id the lead id
     * @param mainInterest the main commercial interest (required)
     * @param note optional commercial note
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws com.fksoft.erp.domain.crm.exception.LeadCannotBeQualifiedException if not in CONTACTED
     * @throws com.fksoft.erp.domain.crm.exception.LeadQualificationRequiresResponsibleException if it has
     *     no responsible person
     */
    @Transactional
    public LeadDetail qualify(
            UUID id, String mainInterest, String note, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Lead lead = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        lead.qualify(userId, mainInterest, note);
        return toDetail(leads.saveAndFlush(lead));
    }

    /**
     * Marks a Lead as lost with a reason and returns the refreshed detail.
     *
     * @param id the lead id
     * @param lossReasonId the (active) loss reason id
     * @param note optional loss note
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws LossReasonNotAvailableException if the loss reason is unknown or inactive
     * @throws com.fksoft.erp.domain.crm.exception.LeadCannotBeMarkedLostException if already lost
     */
    @Transactional
    public LeadDetail markLost(
            UUID id, UUID lossReasonId, String note, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Lead lead = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        LossReason reason = lossReasons
                .findById(lossReasonId)
                .filter(ReferenceData::active)
                .orElseThrow(LossReasonNotAvailableException::new);
        lead.markLost(reason, userId, note);
        return toDetail(leads.saveAndFlush(lead));
    }

    /**
     * Reassigns the responsible person of a Lead (recording assignment history) and returns the
     * refreshed detail. A {@code null} target unassigns the lead.
     *
     * @param id the lead id
     * @param toResponsibleId the new responsible, or {@code null} to unassign
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @param canAssign whether the caller holds full assignment authority ({@code crm:lead:assign})
     * @return the updated detail
     * @throws LeadAssignmentNotAllowedException if a user without assignment authority targets anyone
     *     other than themselves
     * @throws ResponsiblePersonNotFoundException if the target user is unknown or inactive
     */
    @Transactional
    public LeadDetail reassign(
            UUID id,
            UUID toResponsibleId,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned,
            boolean canAssign) {
        Lead lead = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        if (!assignmentPolicy.canAssign(userId, toResponsibleId, canAssign)) {
            throw new LeadAssignmentNotAllowedException();
        }
        if (toResponsibleId != null
                && users.findById(toResponsibleId).filter(User::active).isEmpty()) {
            throw new ResponsiblePersonNotFoundException();
        }
        lead.reassign(toResponsibleId, userId);
        return toDetail(leads.saveAndFlush(lead));
    }

    /**
     * Registers a new interaction in a Lead the caller is allowed to operate and returns the refreshed
     * detail. An effective contact moves a NEW lead to CONTACTED; a non-effective attempt only adds to
     * the history (§ {@link Lead#recordInteraction}).
     *
     * @param id the lead id
     * @param cmd the interaction data (already validated at the boundary)
     * @param userId the acting user (becomes the interaction author)
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the updated detail
     * @throws InteractionTypeNotAvailableException if the type is unknown or inactive
     * @throws InteractionResultNotAvailableException if the result is unknown or inactive
     */
    @Transactional
    public LeadDetail recordInteraction(
            UUID id, RecordInteractionCommand cmd, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Lead lead = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        InteractionType type = interactionTypes
                .findById(cmd.typeId())
                .filter(ReferenceData::active)
                .orElseThrow(InteractionTypeNotAvailableException::new);
        InteractionResult result = interactionResults
                .findById(cmd.resultId())
                .filter(ReferenceData::active)
                .orElseThrow(InteractionResultNotAvailableException::new);
        lead.recordInteraction(type, result, cmd.description(), cmd.occurredAt(), cmd.nextContactAt(), userId);
        return toDetail(leads.saveAndFlush(lead));
    }

    private Lead loadVisible(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Lead lead = leads.findById(id).orElseThrow(LeadNotFoundException::new);
        if (!accessPolicy.canSee(lead, userId, canSeeAll, canSeeUnassigned)) {
            throw new LeadAccessDeniedException();
        }
        return lead;
    }

    private LeadDetail toDetail(Lead lead) {
        Stream.Builder<UUID> ids = Stream.builder();
        ids.add(lead.responsiblePersonId());
        ids.add(lead.qualifiedBy());
        ids.add(lead.lostBy());
        lead.interactions().forEach(i -> ids.add(i.registeredBy()));
        lead.assignments().forEach(a -> {
            ids.add(a.assignedBy());
            ids.add(a.fromResponsibleId());
            ids.add(a.toResponsibleId());
        });
        return LeadDetail.from(lead, resolveNames(ids.build()));
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
