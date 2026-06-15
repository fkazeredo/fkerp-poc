package com.fksoft.erp.domain.crm;

import com.fksoft.erp.domain.identity.ResponsibleView;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
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
 * Application Service of the Commercial / CRM module: registers commercial Leads and serves the
 * operational Lead list. Registering a Lead never creates Customer, Opportunity, Sale, Sales Order,
 * Booking or Financial data. Validates the responsible person directly against Identity
 * (intra-domain collaboration).
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leads;
    private final OriginRepository origins;
    private final InteractionTypeRepository interactionTypes;
    private final UserRepository users;
    private final LeadAccessPolicy accessPolicy;
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
            return toView(lead, latestByLead.get(lead.id()), responsibleName);
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

    private static LeadListView toView(Lead lead, LatestInteractionRow latest, String responsibleName) {
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
