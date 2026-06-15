package com.fksoft.erp.domain.crm;

import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service of the Commercial / CRM module: registers commercial Leads. Registering a Lead
 * never creates Customer, Opportunity, Sale, Sales Order, Booking or Financial data. Validates the
 * responsible person directly against Identity (intra-domain collaboration).
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leads;
    private final OriginRepository origins;
    private final InteractionTypeRepository interactionTypes;
    private final UserRepository users;
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
}
