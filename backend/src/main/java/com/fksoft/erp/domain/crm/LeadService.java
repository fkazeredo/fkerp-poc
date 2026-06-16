package com.fksoft.erp.domain.crm;

import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service (command side) of the Commercial / CRM module: registers Leads and applies the
 * transitions that change them (qualify, mark lost, reassign, record interaction). It never creates
 * Customer, Opportunity, Sale, Sales Order, Booking or Financial data. Reads (list/detail/pending/
 * indicators) live on the read side ({@code application.read.LeadReadService}); these command methods
 * return {@code void} and the controller re-reads the refreshed detail.
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
     * Numbers are matched across both the phone and WhatsApp fields (the same number is often reused);
     * the e-mail is matched case-insensitively. A Lost Lead never blocks (it may be recontacted). The
     * existing Lead id is carried on the error so the caller can open it instead of recreating it.
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
     * Qualifies a Lead with its main commercial interest.
     *
     * @param id the lead id
     * @param mainInterest the main commercial interest (required)
     * @param note optional commercial note
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Lead (manager scope)
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @throws LeadCannotBeQualifiedException if the lead is not in CONTACTED status
     * @throws LeadQualificationRequiresResponsibleException if the lead has no responsible person
     */
    @Transactional
    public void qualify(
            UUID id, String mainInterest, String note, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Lead lead = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        lead.qualify(userId, mainInterest, note);
        leads.saveAndFlush(lead);
    }

    /**
     * Marks a Lead as lost with a reason.
     *
     * @param id the lead id
     * @param lossReasonId the (active) loss reason id
     * @param note optional loss note
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Lead (manager scope)
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @throws LossReasonNotAvailableException if the loss reason is unknown or inactive
     * @throws LeadCannotBeMarkedLostException if the lead is already lost
     */
    @Transactional
    public void markLost(
            UUID id, UUID lossReasonId, String note, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Lead lead = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        LossReason reason = lossReasons
                .findById(lossReasonId)
                .filter(ReferenceData::active)
                .orElseThrow(LossReasonNotAvailableException::new);
        lead.markLost(reason, userId, note);
        leads.saveAndFlush(lead);
    }

    /**
     * Reassigns the responsible person of a Lead (recording assignment history). A {@code null} target
     * unassigns the lead.
     *
     * @param id the lead id
     * @param toResponsibleId the new responsible, or {@code null} to unassign
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Lead (manager scope)
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @param canAssign whether the caller holds full assignment authority ({@code crm:lead:assign})
     * @throws LeadAssignmentNotAllowedException if a user without assignment authority targets
     *     anyone other than themselves
     * @throws ResponsiblePersonNotFoundException if the target user is unknown or inactive
     */
    @Transactional
    public void reassign(
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
        leads.saveAndFlush(lead);
    }

    /**
     * Registers a new interaction in a Lead the caller is allowed to operate. An effective contact
     * moves a NEW lead to CONTACTED; a non-effective attempt only adds to the history
     * (§ {@link Lead#recordInteraction}).
     *
     * @param id the lead id
     * @param cmd the interaction data (already validated at the boundary)
     * @param userId the acting user (becomes the interaction author)
     * @param canSeeAll whether the caller may see every Lead (manager scope)
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @throws InteractionTypeNotAvailableException if the type is unknown or inactive
     * @throws InteractionResultNotAvailableException if the result is unknown or inactive
     */
    @Transactional
    public void recordInteraction(
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
        leads.saveAndFlush(lead);
    }

    /**
     * Loads a Lead the caller is allowed to see, for a command.
     *
     * @param id the lead id
     * @param userId the acting user
     * @param canSeeAll whether the caller may see every Lead (manager scope)
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the lead
     * @throws LeadNotFoundException if no lead has the given id
     * @throws LeadAccessDeniedException if the lead exists but is not visible to the caller
     */
    private Lead loadVisible(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Lead lead = leads.findById(id).orElseThrow(LeadNotFoundException::new);
        if (!accessPolicy.canSee(lead, userId, canSeeAll, canSeeUnassigned)) {
            throw new LeadAccessDeniedException();
        }
        return lead;
    }
}
