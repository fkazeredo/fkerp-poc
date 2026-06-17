package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.sales.model.DiscountType;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalItem;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
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
 * @param total the Proposal total (sum of the items' line totals)
 * @param sourceOpportunity the source Opportunity (kept traceable; still the system of record)
 */
public record ProposalDetail(
        UUID id,
        UUID opportunityId,
        UUID leadId,
        ProposalStatus status,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        String title,
        String notes,
        LocalDate validUntil,
        String commercialTerms,
        List<Item> items,
        BigDecimal total,
        Instant createdAt,
        Instant updatedAt,
        SourceOpportunity sourceOpportunity) {

    /**
     * Assembles the detail from the Proposal, its source Opportunity and the resolved user names.
     *
     * @param p the proposal aggregate (with its items loaded)
     * @param opportunity the source Opportunity (loaded for traceability)
     * @param names map of user id → display name for the responsible
     * @return the detail read model
     */
    public static ProposalDetail from(Proposal p, Opportunity opportunity, Map<UUID, String> names) {
        List<Item> items = p.items().stream()
                .sorted(Comparator.comparing(ProposalItem::createdAt))
                .map(Item::from)
                .toList();
        return new ProposalDetail(
                p.id(),
                p.opportunityId(),
                p.leadId(),
                p.status(),
                p.responsiblePersonId(),
                nameOf(names, p.responsiblePersonId()),
                p.responsiblePersonId() == null,
                p.title(),
                p.notes(),
                p.validUntil(),
                p.commercialTerms(),
                items,
                p.total(),
                p.createdAt(),
                p.updatedAt(),
                new SourceOpportunity(opportunity.id(), opportunity.name(), opportunity.stage()));
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    /** The source Opportunity, kept traceable from the Proposal. */
    public record SourceOpportunity(UUID id, String name, OpportunityStage stage) {}

    /** A single commercial-offer line, with its computed line total. */
    public record Item(
            UUID id,
            ProposalItemType type,
            String description,
            int quantity,
            BigDecimal unitValue,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal lineTotal) {

        static Item from(ProposalItem i) {
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
