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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A commercial Lead: an initial interested person or company in the funnel. A Lead is NOT a Customer,
 * Opportunity, Sale, Sales Order, Booking, Passenger, Payer or Customer Care ticket. Aggregate root
 * of the Commercial / CRM module.
 */
@Entity
@Table(name = "leads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lead {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String whatsapp;
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_id", nullable = false)
    private Origin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStatus status;

    @Column(name = "responsible_person_id")
    private UUID responsiblePersonId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "lead_id", nullable = false)
    private List<LeadInteraction> interactions = new ArrayList<>();

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
     * the responsible person is optional (the lead is then a pending assignment).
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
