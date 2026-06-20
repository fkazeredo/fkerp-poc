package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderItem;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import com.fksoft.erp.domain.sales.model.DiscountType;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full Commercial Order detail (read model): the formal record of the closed deal, assembled from the Order
 * (with its item snapshot) plus its source Proposal, Opportunity and Lead (kept traceable) and a map of user
 * id → display name. Exposes commercial-order data only — never Booking, Receivable, Payment, Commission or
 * Customer Care data.
 *
 * @param items the order lines (snapshot of the Proposal's items), each with its computed line total
 * @param subtotal the items subtotal (snapshot from the Proposal)
 * @param total the order total (snapshot from the Proposal)
 * @param sourceProposal the source Proposal (preserved)
 * @param sourceOpportunity the source Opportunity (preserved; now marked won)
 * @param sourceLead the source Lead (preserved; still the system of record for the contact)
 */
public record CommercialOrderDetail(
        UUID id,
        long number,
        UUID proposalId,
        UUID opportunityId,
        UUID leadId,
        CommercialOrderStatus status,
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
                o.status(),
                o.responsiblePersonId(),
                nameOf(names, o.responsiblePersonId()),
                o.responsiblePersonId() == null,
                items,
                o.subtotal(),
                o.total(),
                o.createdAt(),
                nameOf(names, o.createdBy()),
                new SourceProposal(proposal.id(), proposal.title(), proposal.status()),
                new SourceOpportunity(opportunity.id(), opportunity.name(), opportunity.stage()),
                new SourceLead(lead.id(), lead.name(), lead.phone(), lead.whatsapp(), lead.email(), lead.status()));
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    /** The source Proposal, preserved by the Order. */
    public record SourceProposal(UUID id, String title, ProposalStatus status) {}

    /** The source Opportunity, kept traceable from the Order. */
    public record SourceOpportunity(UUID id, String name, OpportunityStage stage) {}

    /** The source Lead, kept traceable from the Order (the contact's system of record). */
    public record SourceLead(UUID id, String name, String phone, String whatsapp, String email, LeadStatus status) {}

    /** A single order line (snapshot of a Proposal item), with its computed line total. */
    public record Item(
            UUID id,
            ProposalItemType type,
            String description,
            int quantity,
            BigDecimal unitValue,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal lineTotal) {

        static Item from(CommercialOrderItem i) {
            return new Item(
                    i.id(),
                    i.type(),
                    i.description(),
                    i.quantity(),
                    i.unitValue(),
                    i.discountType(),
                    i.discountValue(),
                    i.lineTotal());
        }
    }
}
