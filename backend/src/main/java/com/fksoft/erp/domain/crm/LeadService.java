package com.fksoft.erp.domain.crm;

import com.fksoft.erp.domain.identity.ResponsibleView;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service of the Commercial / CRM module: registers Leads, serves the operational list,
 * and serves the Lead detail with its commercial history plus the transitions that produce it
 * (qualify, mark lost, reassign). Never creates Customer, Opportunity, Sale, Sales Order, Booking or
 * Financial data. Collaborates with Identity (responsible/actor resolution) directly.
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
     * Operational, paginated Lead list filtered by the given criteria and the caller's visibility.
     * The visibility predicate is applied at the query level, so filters and search can never expose
     * Leads the caller is not allowed to see.
     *
     * @param criteria the (optional) filters
     * @param pageable page, size and sort
     * @param currentUserId the calling user
     * @param canSeeAll whether the caller may see every Lead (manager scope)
     * @return a page of operational Lead views
     */
    @Transactional(readOnly = true)
    public Page<LeadListView> list(
            LeadSearchCriteria criteria, Pageable pageable, UUID currentUserId, boolean canSeeAll) {
        Specification<Lead> spec =
                LeadSpecifications.matching(criteria).and(accessPolicy.visibleTo(currentUserId, canSeeAll));
        Page<Lead> page = leads.findAll(spec, pageable);

        List<UUID> leadIds = page.getContent().stream().map(Lead::id).toList();
        Map<UUID, LatestInteractionRow> latestByLead = leadIds.isEmpty()
                ? Map.of()
                : leads.findLatestInteractions(leadIds).stream()
                        .collect(Collectors.toMap(LatestInteractionRow::getLeadId, row -> row, (a, b) -> a));
        Set<UUID> responsibleIds = page.getContent().stream()
                .map(Lead::responsiblePersonId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> namesById = responsibleIds.isEmpty()
                ? Map.of()
                : users.findAllById(responsibleIds).stream().collect(Collectors.toMap(User::id, User::username));

        return page.map(lead -> {
            UUID responsibleId = lead.responsiblePersonId();
            String responsibleName = responsibleId == null ? null : namesById.get(responsibleId);
            return toListView(lead, latestByLead.get(lead.id()), responsibleName);
        });
    }

    /**
     * Active users that can be assigned as a Lead responsible (for the assignment selector and the
     * responsible filter).
     *
     * @return active users as lightweight views, ordered by username
     */
    @Transactional(readOnly = true)
    public List<ResponsibleView> listResponsibles() {
        return users.findByActiveTrueOrderByUsernameAsc().stream()
                .map(ResponsibleView::from)
                .toList();
    }

    /**
     * Full detail of a Lead the caller is allowed to see, including its commercial history.
     *
     * @param id the lead id
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Lead (manager scope)
     * @return the lead detail view
     * @throws LeadNotFoundException if no lead has the given id
     * @throws LeadAccessDeniedException if the lead exists but is not visible to the caller
     */
    @Transactional(readOnly = true)
    public LeadDetailView detail(UUID id, UUID userId, boolean canSeeAll) {
        return toDetailView(loadVisible(id, userId, canSeeAll));
    }

    /**
     * Qualifies a Lead and returns the refreshed detail.
     *
     * @param id the lead id
     * @param note optional qualification note
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Lead (manager scope)
     * @return the updated detail
     * @throws LeadCannotBeQualifiedException if the lead is already qualified or lost
     */
    @Transactional
    public LeadDetailView qualify(UUID id, String note, UUID userId, boolean canSeeAll) {
        Lead lead = loadVisible(id, userId, canSeeAll);
        lead.qualify(userId, note);
        return toDetailView(leads.saveAndFlush(lead));
    }

    /**
     * Marks a Lead as lost with a reason and returns the refreshed detail.
     *
     * @param id the lead id
     * @param lossReasonId the (active) loss reason id
     * @param note optional loss note
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Lead (manager scope)
     * @return the updated detail
     * @throws LossReasonNotAvailableException if the loss reason is unknown or inactive
     * @throws LeadCannotBeMarkedLostException if the lead is already lost
     */
    @Transactional
    public LeadDetailView markLost(UUID id, UUID lossReasonId, String note, UUID userId, boolean canSeeAll) {
        Lead lead = loadVisible(id, userId, canSeeAll);
        LossReason reason = lossReasons
                .findById(lossReasonId)
                .filter(ReferenceData::active)
                .orElseThrow(LossReasonNotAvailableException::new);
        lead.markLost(reason, userId, note);
        return toDetailView(leads.saveAndFlush(lead));
    }

    /**
     * Reassigns the responsible person of a Lead (recording assignment history) and returns the
     * refreshed detail. A {@code null} target unassigns the lead.
     *
     * @param id the lead id
     * @param toResponsibleId the new responsible, or {@code null} to unassign
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Lead (manager scope)
     * @param canAssign whether the caller holds full assignment authority ({@code crm:lead:assign})
     * @return the updated detail
     * @throws LeadAssignmentNotAllowedException if a user without assignment authority targets
     *     anyone other than themselves
     * @throws ResponsiblePersonNotFoundException if the target user is unknown or inactive
     */
    @Transactional
    public LeadDetailView reassign(UUID id, UUID toResponsibleId, UUID userId, boolean canSeeAll, boolean canAssign) {
        Lead lead = loadVisible(id, userId, canSeeAll);
        if (!assignmentPolicy.canAssign(userId, toResponsibleId, canAssign)) {
            throw new LeadAssignmentNotAllowedException();
        }
        if (toResponsibleId != null
                && users.findById(toResponsibleId).filter(User::active).isEmpty()) {
            throw new ResponsiblePersonNotFoundException();
        }
        lead.reassign(toResponsibleId, userId);
        return toDetailView(leads.saveAndFlush(lead));
    }

    /**
     * Registers a new interaction in a Lead the caller is allowed to operate and returns the refreshed
     * detail. An effective contact moves a NEW lead to CONTACTED; a non-effective attempt only adds to
     * the history (§ {@link Lead#recordInteraction}).
     *
     * @param id the lead id
     * @param cmd the interaction data (already validated at the boundary)
     * @param userId the acting user (becomes the interaction author)
     * @param canSeeAll whether the caller may see every Lead (manager scope)
     * @return the updated detail
     * @throws InteractionTypeNotAvailableException if the type is unknown or inactive
     * @throws InteractionResultNotAvailableException if the result is unknown or inactive
     */
    @Transactional
    public LeadDetailView recordInteraction(UUID id, RecordInteractionCommand cmd, UUID userId, boolean canSeeAll) {
        Lead lead = loadVisible(id, userId, canSeeAll);
        InteractionType type = interactionTypes
                .findById(cmd.typeId())
                .filter(ReferenceData::active)
                .orElseThrow(InteractionTypeNotAvailableException::new);
        InteractionResult result = interactionResults
                .findById(cmd.resultId())
                .filter(ReferenceData::active)
                .orElseThrow(InteractionResultNotAvailableException::new);
        lead.recordInteraction(type, result, cmd.description(), cmd.occurredAt(), cmd.nextContactAt(), userId);
        return toDetailView(leads.saveAndFlush(lead));
    }

    private Lead loadVisible(UUID id, UUID userId, boolean canSeeAll) {
        Lead lead = leads.findById(id).orElseThrow(LeadNotFoundException::new);
        if (!accessPolicy.canSee(lead, userId, canSeeAll)) {
            throw new LeadAccessDeniedException();
        }
        return lead;
    }

    private LeadDetailView toDetailView(Lead lead) {
        Set<UUID> userIds = new HashSet<>();
        addIfPresent(userIds, lead.responsiblePersonId());
        addIfPresent(userIds, lead.qualifiedBy());
        addIfPresent(userIds, lead.lostBy());
        lead.interactions().forEach(i -> addIfPresent(userIds, i.registeredBy()));
        lead.assignments().forEach(a -> {
            addIfPresent(userIds, a.assignedBy());
            addIfPresent(userIds, a.fromResponsibleId());
            addIfPresent(userIds, a.toResponsibleId());
        });
        Map<UUID, String> names = userIds.isEmpty()
                ? new HashMap<>()
                : users.findAllById(userIds).stream().collect(Collectors.toMap(User::id, User::username));

        List<InteractionView> interactions = lead.interactions().stream()
                .sorted(Comparator.comparing(LeadInteraction::occurredAt).reversed())
                .map(i -> new InteractionView(
                        i.id(),
                        i.type().label(),
                        i.result() != null ? i.result().label() : null,
                        i.content(),
                        i.occurredAt(),
                        i.nextContactAt(),
                        names.get(i.registeredBy())))
                .toList();
        List<AssignmentView> assignments = lead.assignments().stream()
                .sorted(Comparator.comparing(LeadAssignment::assignedAt).reversed())
                .map(a -> new AssignmentView(
                        nameOf(names, a.fromResponsibleId()),
                        nameOf(names, a.toResponsibleId()),
                        names.get(a.assignedBy()),
                        a.assignedAt()))
                .toList();
        QualificationView qualification = lead.qualifiedAt() == null
                ? null
                : new QualificationView(lead.qualifiedAt(), names.get(lead.qualifiedBy()), lead.qualificationNote());
        LossView loss = lead.lostAt() == null
                ? null
                : new LossView(
                        lead.lossReason() != null ? lead.lossReason().label() : null,
                        lead.lostAt(),
                        names.get(lead.lostBy()),
                        lead.lossNote());

        return new LeadDetailView(
                lead.id(),
                lead.name(),
                lead.phone(),
                lead.whatsapp(),
                lead.email(),
                lead.origin().label(),
                lead.status(),
                lead.responsiblePersonId(),
                nameOf(names, lead.responsiblePersonId()),
                lead.createdAt(),
                lead.updatedAt(),
                lead.nextContactAt(),
                interactions,
                assignments,
                qualification,
                loss);
    }

    private static LeadListView toListView(Lead lead, LatestInteractionRow latest, String responsibleName) {
        return new LeadListView(
                lead.id(),
                lead.name(),
                mainContact(lead),
                lead.origin().label(),
                lead.status(),
                lead.responsiblePersonId(),
                responsibleName,
                lead.createdAt(),
                latest != null ? latest.getOccurredAt() : null,
                latest != null ? latest.getTypeLabel() : null,
                lead.nextContactAt());
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    private static void addIfPresent(Set<UUID> ids, UUID id) {
        if (id != null) {
            ids.add(id);
        }
    }

    private static String mainContact(Lead lead) {
        if (lead.phone() != null) {
            return lead.phone();
        }
        if (lead.whatsapp() != null) {
            return lead.whatsapp();
        }
        return lead.email();
    }
}
