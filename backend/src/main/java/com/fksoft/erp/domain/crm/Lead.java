package com.fksoft.erp.domain.crm;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A commercial Lead: an initial interested person or company in the funnel. A Lead is NOT a Customer,
 * Opportunity, Sale, Sales Order, Booking, Passenger, Payer or Customer Care ticket. Aggregate root
 * of the Commercial / CRM module; it owns its interaction and assignment history and the
 * qualification / loss outcome.
 */
@Entity
@Table(name = "leads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lead {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false)
    private String name;

    @Pattern(regexp = "\\d*")
    @Size(max = 30)
    private String phone;

    @Pattern(regexp = "\\d*")
    @Size(max = 30)
    private String whatsapp;

    @Email
    @Size(max = 255)
    private String email;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_id", nullable = false)
    private Origin origin;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStatus status;

    @Column(name = "responsible_person_id")
    private UUID responsiblePersonId;

    // Scheduled date for the next contact. Read-only in this slice (shown when present); a later
    // follow-up slice will set it.
    @Column(name = "next_contact_at")
    private Instant nextContactAt;

    // Qualification outcome (set when the lead is qualified; kept for history and to seed a future
    // Opportunity). The main interest is required at qualification; the note is optional.
    @Column(name = "qualified_at")
    private Instant qualifiedAt;

    @Column(name = "qualified_by")
    private UUID qualifiedBy;

    @Size(max = 500)
    @Column(name = "main_interest")
    private String mainInterest;

    @Size(max = 2000)
    @Column(name = "qualification_note")
    private String qualificationNote;

    // Loss outcome (set when the lead is marked lost; kept for history).
    @Column(name = "lost_at")
    private Instant lostAt;

    @Column(name = "lost_by")
    private UUID lostBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loss_reason_id")
    private LossReason lossReason;

    @Size(max = 2000)
    @Column(name = "loss_note")
    private String lossNote;

    @Valid
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "lead_id", nullable = false)
    private List<LeadInteraction> interactions = new ArrayList<>();

    @Valid
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "lead_id", nullable = false)
    private List<LeadAssignment> assignments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    /**
     * Registers a new Lead in status {@link LeadStatus#NEW}. Requires at least one contact method;
     * the responsible person is optional (the lead is then a pending assignment). When a responsible
     * is set, the first assignment-history entry is recorded.
     *
     * @param command the lead data
     * @param origin the (active) origin cadastro value
     * @param createdBy id of the user creating the lead
     * @return a new, unsaved Lead
     * @throws LeadContactRequiredException if no phone, WhatsApp or e-mail is provided
     */
    public static Lead register(RegisterLeadCommand command, Origin origin, UUID createdBy) {
        if (isBlank(command.phone()) && isBlank(command.whatsapp()) && isBlank(command.email())) {
            throw new LeadContactRequiredException();
        }
        Lead lead = new Lead();
        lead.id = UUID.randomUUID();
        lead.name = command.name();
        lead.phone = emptyToNull(command.phone());
        lead.whatsapp = emptyToNull(command.whatsapp());
        lead.email = emptyToNull(command.email());
        lead.origin = origin;
        lead.status = LeadStatus.NEW;
        lead.responsiblePersonId = command.responsiblePersonId();
        lead.createdBy = createdBy;
        lead.updatedBy = createdBy;
        if (lead.responsiblePersonId != null) {
            lead.assignments.add(LeadAssignment.of(null, lead.responsiblePersonId, createdBy));
        }
        return lead;
    }

    /**
     * Records the initial note as the first interaction in the lead history.
     *
     * @param noteType the "internal note" interaction type
     * @param content the note text
     * @param registeredBy id of the user recording the note
     */
    public void addInitialNote(InteractionType noteType, String content, UUID registeredBy) {
        interactions.add(LeadInteraction.note(noteType, content, registeredBy));
    }

    /**
     * Registers a new interaction (contact, attempt or note) in the lead history. The history is
     * append-only. An <em>effective</em> contact (see {@link InteractionResult#isEffectiveContact()})
     * moves a {@link LeadStatus#NEW} lead to {@link LeadStatus#CONTACTED}; a non-effective attempt
     * preserves the history but leaves the status untouched, and no other status ever changes here.
     * When the interaction schedules a next contact, it becomes the lead's current next-contact date.
     *
     * @param type the interaction type (active)
     * @param result the interaction result (active)
     * @param content the description (required)
     * @param occurredAt when the interaction happened (past or present)
     * @param nextContactAt the scheduled next contact, or {@code null}
     * @param registeredBy id of the user recording the interaction
     */
    public void recordInteraction(
            InteractionType type,
            InteractionResult result,
            String content,
            Instant occurredAt,
            Instant nextContactAt,
            UUID registeredBy) {
        interactions.add(LeadInteraction.record(type, result, content, occurredAt, nextContactAt, registeredBy));
        if (status == LeadStatus.NEW && result.isEffectiveContact()) {
            status = LeadStatus.CONTACTED;
        }
        if (nextContactAt != null) {
            this.nextContactAt = nextContactAt;
        }
        updatedBy = registeredBy;
    }

    /**
     * Qualifies the lead with its main commercial interest. Allowed only from
     * {@link LeadStatus#CONTACTED} (the lead must have an effective contact first) and only when the
     * lead has a responsible person; the qualification (interest/who/when/note) is kept for history
     * and to seed a future Opportunity. The lead already carries valid contact information by
     * construction ({@link #register}), so that precondition cannot be violated here.
     *
     * @param byUser id of the user qualifying the lead
     * @param mainInterest the main commercial interest (required)
     * @param note optional commercial note
     * @throws LeadCannotBeQualifiedException if the lead is not in CONTACTED status (NEW, already
     *     qualified, or lost)
     * @throws LeadQualificationRequiresResponsibleException if the lead has no responsible person
     */
    public void qualify(UUID byUser, String mainInterest, String note) {
        if (status != LeadStatus.CONTACTED) {
            throw new LeadCannotBeQualifiedException();
        }
        if (responsiblePersonId == null) {
            throw new LeadQualificationRequiresResponsibleException();
        }
        status = LeadStatus.QUALIFIED;
        qualifiedAt = Instant.now();
        qualifiedBy = byUser;
        this.mainInterest = mainInterest;
        qualificationNote = emptyToNull(note);
        updatedBy = byUser;
    }

    /**
     * Marks the lead as lost with a reason. Allowed from any non-lost status; the loss
     * (reason/who/when/note) is kept for history.
     *
     * @param reason the (active) loss reason
     * @param byUser id of the user marking the lead lost
     * @param note optional loss note
     * @throws LeadCannotBeMarkedLostException if the lead is already lost
     */
    public void markLost(LossReason reason, UUID byUser, String note) {
        if (status == LeadStatus.LOST) {
            throw new LeadCannotBeMarkedLostException();
        }
        status = LeadStatus.LOST;
        lossReason = reason;
        lostAt = Instant.now();
        lostBy = byUser;
        lossNote = emptyToNull(note);
        updatedBy = byUser;
    }

    /**
     * Reassigns the responsible person, recording an assignment-history entry. A no-op when the
     * responsible does not actually change.
     *
     * @param toResponsibleId the new responsible (or {@code null} to unassign)
     * @param byUser id of the user performing the reassignment
     */
    public void reassign(UUID toResponsibleId, UUID byUser) {
        if (Objects.equals(responsiblePersonId, toResponsibleId)) {
            return;
        }
        assignments.add(LeadAssignment.of(responsiblePersonId, toResponsibleId, byUser));
        responsiblePersonId = toResponsibleId;
        updatedBy = byUser;
    }

    public boolean hasResponsible() {
        return responsiblePersonId != null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }
}
