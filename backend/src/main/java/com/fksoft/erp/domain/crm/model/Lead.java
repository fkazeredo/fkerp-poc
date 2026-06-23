package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.crm.exception.LeadContactRequiredException;
import com.fksoft.erp.domain.crm.service.data.RegisterLeadCommand;
import com.fksoft.erp.domain.workflow.WorkflowState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    // Denormalized state code (== currentState.code()), kept for cheap filtering/grouping and the read
    // contract; the data-driven source of truth is the workflow state below, kept in sync on every transition.
    @NotBlank
    @Size(max = 60)
    @Column(nullable = false)
    private String status;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_state_id", nullable = false)
    private WorkflowState currentState;

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
     * Registers a new Lead in the workflow's initial state ({@code NEW}). Requires at least one contact
     * method; the responsible person is optional (the lead is then a pending assignment). When a
     * responsible is set, the first assignment-history entry is recorded.
     *
     * @param command the lead data
     * @param origin the (active) origin cadastro value
     * @param initialState the Lead workflow's initial state ({@code NEW})
     * @param createdBy id of the user creating the lead
     * @return a new, unsaved Lead
     * @throws LeadContactRequiredException if no phone, WhatsApp or e-mail is provided
     */
    public static Lead register(
            RegisterLeadCommand command, Origin origin, WorkflowState initialState, UUID createdBy) {
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
        lead.currentState = initialState;
        lead.status = initialState.code();
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
     * append-only. The status change on an <em>effective</em> contact (NEW → CONTACTED) is driven by the
     * workflow engine in the application service (the {@code contact} system transition), not here. When
     * the interaction schedules a next contact, it becomes the lead's current next-contact date.
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
        if (nextContactAt != null) {
            this.nextContactAt = nextContactAt;
        }
        updatedBy = registeredBy;
    }

    /**
     * Moves the lead into the given workflow state (the {@code contact} system transition: NEW →
     * CONTACTED), after the engine has validated it. Records no extra data beyond the audit fields.
     *
     * @param target the destination workflow state
     * @param byUser id of the user whose action triggered the transition
     */
    public void markContacted(WorkflowState target, UUID byUser) {
        currentState = target;
        status = target.code();
        updatedBy = byUser;
    }

    /**
     * Applies the qualification outcome onto the lead and moves it to the given state (the {@code qualify}
     * transition), after the engine has validated it (state + require-responsible). The qualification
     * (interest/who/when/note) is kept for history and to seed a future Opportunity.
     *
     * @param target the destination workflow state ({@code QUALIFIED})
     * @param mainInterest the main commercial interest (required)
     * @param note optional commercial note
     * @param byUser id of the user qualifying the lead
     */
    public void applyQualification(WorkflowState target, String mainInterest, String note, UUID byUser) {
        currentState = target;
        status = target.code();
        qualifiedAt = Instant.now();
        qualifiedBy = byUser;
        this.mainInterest = mainInterest;
        qualificationNote = emptyToNull(note);
        updatedBy = byUser;
    }

    /**
     * Applies the loss outcome onto the lead and moves it to the given state (the {@code lose} transition),
     * after the engine has validated it. The loss (reason/who/when/note) is kept for history.
     *
     * @param target the destination workflow state ({@code LOST})
     * @param reason the (active) loss reason
     * @param byUser id of the user marking the lead lost
     * @param note optional loss note
     */
    public void applyLoss(WorkflowState target, LossReason reason, UUID byUser, String note) {
        currentState = target;
        status = target.code();
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
