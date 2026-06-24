package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderItem;
import com.fksoft.erp.domain.sales.model.DiscountType;
import com.fksoft.erp.domain.sales.model.Proposal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full Commercial Order detail (read model): the formal record of the closed deal, assembled from the Order
 * (with its item snapshot) plus its source Proposal, Opportunity and Lead (kept traceable) and a map of user
 * id → display name. Exposes commercial-order data plus the booking and financial status reflections only —
 * never Receivable, Payment, Commission or Customer Care detail.
 *
 * @param requiresBooking whether the Order still needs a reservation (the explicit booking-need indicator;
 *     {@code true} when the status is PENDING_BOOKING) — what Sprint 4 Booking Operations keys off
 * @param bookingStatus the consolidated booking status reflected from Booking Operations, or {@code null} when
 *     no Booking Request exists yet; {@code CONFIRMED} marks the Order ready for Financial Operations and
 *     {@code FAILED} marks a booking problem (a read-only reflection — it never drives the Order's own status)
 * @param financialStatus the Receivable status reflected from Financial Operations, or {@code null} when no
 *     Receivable exists yet; {@code PAID} marks the Order ready for Commission Management (Sprint 6) and
 *     {@code OVERDUE} marks a financial problem (a read-only reflection — it never drives the Order's own status)
 * @param items the order lines (snapshot of the Proposal's items), each with its computed line total
 * @param subtotal the items subtotal (snapshot from the Proposal)
 * @param total the order total (snapshot from the Proposal)
 * @param sourceProposal the source Proposal (preserved), with its commercial terms / validity / notes
 *     surfaced for convenience (read from the immutable source Proposal — not duplicated onto the Order)
 * @param sourceOpportunity the source Opportunity (preserved; now marked won)
 * @param sourceLead the source Lead (preserved; still the system of record for the contact)
 */
public record CommercialOrderDetail(
        UUID id,
        long number,
        UUID proposalId,
        UUID opportunityId,
        UUID leadId,
        String status,
        boolean requiresBooking,
        String bookingStatus,
        String financialStatus,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        List<Item> items,
        BigDecimal subtotal,
        BigDecimal total,
        Instant createdAt,
        String createdByName,
        SourceProposal sourceProposal,
        SourceOpportunity sourceOpportunity,
        SourceLead sourceLead) {

    /**
     * Assembles the detail from the Order, its source Proposal/Opportunity/Lead and the resolved user names.
     *
     * @param o the order aggregate (with its items loaded)
     * @param proposal the source Proposal
     * @param opportunity the source Opportunity
     * @param lead the source Lead
     * @param names map of user id → display name for the responsible and the creator
     * @return the detail read model
     */
    public static CommercialOrderDetail from(
            CommercialOrder o, Proposal proposal, Opportunity opportunity, Lead lead, Map<UUID, String> names) {
        List<Item> items = o.items().stream()
                .sorted(Comparator.comparing(CommercialOrderItem::createdAt))
                .map(Item::from)
                .toList();
        return new CommercialOrderDetail(
                o.id(),
                o.number(),
                o.proposalId(),
                o.opportunityId(),
                o.leadId(),
                o.status().name(),
                "PENDING_BOOKING".equals(o.status().name()),
                o.bookingStatus(),
                o.financialStatus(),
                o.responsiblePersonId(),
                nameOf(names, o.responsiblePersonId()),
                o.responsiblePersonId() == null,
                items,
                o.subtotal(),
                o.total(),
                o.createdAt(),
                nameOf(names, o.createdBy()),
                new SourceProposal(
                        proposal.id(),
                        proposal.title(),
                        proposal.status().name(),
                        proposal.validUntil(),
                        proposal.commercialTerms(),
                        proposal.notes(),
                        proposal.paymentNotes()),
                new SourceOpportunity(
                        opportunity.id(),
                        opportunity.name(),
                        opportunity.stage().name()),
                new SourceLead(
                        lead.id(),
                        lead.name(),
                        lead.phone(),
                        lead.whatsapp(),
                        lead.email(),
                        lead.status().name()));
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    /**
     * The source Proposal, preserved by the Order. Carries its commercial context — validity, commercial
     * terms, notes and payment notes — surfaced (read from the immutable source Proposal, not duplicated onto
     * the Order) so the Order detail is ready for Sprint 4 Booking without recapturing the commercial data.
     */
    public record SourceProposal(
            UUID id,
            String title,
            String status,
            LocalDate validUntil,
            String commercialTerms,
            String notes,
            String paymentNotes) {}

    /** The source Opportunity, kept traceable from the Order. */
    public record SourceOpportunity(UUID id, String name, String stage) {}

    /** The source Lead, kept traceable from the Order (the contact's system of record). */
    public record SourceLead(UUID id, String name, String phone, String whatsapp, String email, String status) {}

    /** A single order line (snapshot of a Proposal item), with its computed line total. */
    public record Item(
            UUID id,
            String type,
            String typeLabel,
            String description,
            int quantity,
            BigDecimal unitValue,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal lineTotal) {

        static Item from(CommercialOrderItem i) {
            return new Item(
                    i.id(),
                    i.type().code(),
                    i.type().label(),
                    i.description(),
                    i.quantity(),
                    i.unitValue(),
                    i.discountType(),
                    i.discountValue(),
                    i.lineTotal());
        }
    }
}
