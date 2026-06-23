package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.sales.model.DiscountType;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalItem;
import com.fksoft.erp.domain.sales.model.ProposalStatusChange;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full Proposal detail (read model), assembled from the Proposal (with its items) plus its source
 * Opportunity (for traceability) and a map of user id → display name. Exposes commercial-offer data only
 * — never Sale, Sales Order, Booking, Financial, Payment, Commission or Customer data.
 *
 * @param items the commercial-offer lines (oldest first), each with its computed line total
 * @param subtotal the items subtotal (sum of the items' line totals)
 * @param discountType the optional Proposal-level discount type (null when there is none)
 * @param discountValue the optional Proposal-level discount value (null when there is none)
 * @param total the Proposal total (subtotal minus the Proposal-level discount; never negative)
 * @param paymentNotes descriptive payment notes (free text only — not a Financial record)
 * @param sourceOpportunity the source Opportunity (kept traceable; still the system of record)
 * @param sourceLead the source Lead (kept traceable; still the system of record for the contact)
 * @param statusHistory the lifecycle status-change history (newest first); empty until the first transition
 * @param rejectionReason the rejection reason (present only when the Proposal was rejected at review)
 * @param rejectionNote the optional rejection note (present only when the Proposal was rejected)
 * @param sendingChannel the optional channel the Proposal was sent through (present only once it was sent)
 * @param acceptanceNote the optional client confirmation note (present only when the client accepted)
 * @param customerRejectionReason the customer-rejection reason (present only when the client rejected)
 * @param customerRejectionNote the optional customer-rejection note (present only when the client rejected)
 * @param commercialOrderId the Proposal's active Commercial Order id (present once an Order was created), else null
 */
public record ProposalDetail(
        UUID id,
        UUID opportunityId,
        UUID leadId,
        String status,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        String title,
        String notes,
        LocalDate validUntil,
        String commercialTerms,
        String paymentNotes,
        List<Item> items,
        BigDecimal subtotal,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal total,
        Instant createdAt,
        Instant updatedAt,
        SourceOpportunity sourceOpportunity,
        SourceLead sourceLead,
        List<StatusChange> statusHistory,
        String rejectionReason,
        String rejectionNote,
        String sendingChannel,
        String acceptanceNote,
        String customerRejectionReason,
        String customerRejectionNote,
        UUID commercialOrderId) {

    /**
     * Assembles the detail from the Proposal, its source Opportunity and Lead, and the resolved user names.
     *
     * @param p the proposal aggregate (with its items and status history loaded)
     * @param opportunity the source Opportunity (loaded for traceability)
     * @param lead the source Lead (loaded for the contact reference)
     * @param names map of user id → display name for the responsible and the history actors
     * @param commercialOrderId the id of the Proposal's active Commercial Order, or {@code null} when none
     * @return the detail read model
     */
    public static ProposalDetail from(
            Proposal p, Opportunity opportunity, Lead lead, Map<UUID, String> names, UUID commercialOrderId) {
        List<Item> items = p.items().stream()
                .sorted(Comparator.comparing(ProposalItem::createdAt))
                .map(Item::from)
                .toList();
        List<StatusChange> statusHistory = p.statusChanges().stream()
                .sorted(Comparator.comparing(ProposalStatusChange::changedAt).reversed())
                .map(c -> new StatusChange(c.fromStatus(), c.toStatus(), c.changedAt(), nameOf(names, c.changedBy())))
                .toList();
        return new ProposalDetail(
                p.id(),
                p.opportunityId(),
                p.leadId(),
                p.status().name(),
                p.responsiblePersonId(),
                nameOf(names, p.responsiblePersonId()),
                p.responsiblePersonId() == null,
                p.title(),
                p.notes(),
                p.validUntil(),
                p.commercialTerms(),
                p.paymentNotes(),
                items,
                p.subtotal(),
                p.discountType(),
                p.discountValue(),
                p.total(),
                p.createdAt(),
                p.updatedAt(),
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
                        lead.status().name()),
                statusHistory,
                p.rejectionReason() == null ? null : p.rejectionReason().label(),
                p.rejectionNote(),
                p.sendingChannel() == null ? null : p.sendingChannel().label(),
                p.acceptanceNote(),
                p.customerRejectionReason() == null
                        ? null
                        : p.customerRejectionReason().label(),
                p.customerRejectionNote(),
                commercialOrderId);
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    /** The source Opportunity, kept traceable from the Proposal. */
    public record SourceOpportunity(UUID id, String name, String stage) {}

    /** The source Lead, kept traceable from the Proposal (the contact's system of record). */
    public record SourceLead(UUID id, String name, String phone, String whatsapp, String email, String status) {}

    /** A single Proposal status-change entry (from → to, when, by whom). */
    public record StatusChange(String from, String to, Instant at, String by) {}

    /** A single commercial-offer line, with its computed line total. */
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

        static Item from(ProposalItem i) {
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
