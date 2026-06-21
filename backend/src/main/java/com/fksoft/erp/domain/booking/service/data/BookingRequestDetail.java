package com.fksoft.erp.domain.booking.service.data;

import com.fksoft.erp.domain.booking.model.BookingItem;
import com.fksoft.erp.domain.booking.model.BookingItemStatus;
import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full Booking Request detail (read model): the operational reservation record, assembled from the request
 * (with its item snapshot) plus its source Commercial Order, Proposal, Opportunity and Lead (kept traceable)
 * and a map of user id → display name. It lets the operations user see <b>what must be reserved, what is
 * confirmed and what failed</b> — confirmation/failure are shown per item via the item status (the richer
 * attempt log / confirmation reference / failure reason are later slices). Exposes operational reservation
 * data only — <b>never</b> Financial, Payment or Commission data.
 *
 * @param itemsRequiringBooking how many items require a booking operation
 * @param itemsConfirmed how many items are confirmed
 * @param itemsFailed how many items failed and need an operational decision
 * @param items the booking lines, each traceable to its source Commercial Order item ({@code orderItemId})
 * @param sourceOrder the source Commercial Order (preserved; always traceable)
 * @param sourceProposal the source Proposal (commercial reference)
 * @param sourceOpportunity the source Opportunity (commercial reference)
 * @param sourceLead the source Lead (kept traceable)
 */
public record BookingRequestDetail(
        UUID id,
        UUID commercialOrderId,
        long commercialOrderNumber,
        BookingRequestStatus status,
        UUID bookingOperatorId,
        String bookingOperatorName,
        boolean operatorUnassigned,
        UUID responsiblePersonId,
        String responsibleName,
        String notes,
        long itemsRequiringBooking,
        long itemsConfirmed,
        long itemsFailed,
        Instant createdAt,
        Instant updatedAt,
        String createdByName,
        List<Item> items,
        SourceOrder sourceOrder,
        SourceProposal sourceProposal,
        SourceOpportunity sourceOpportunity,
        SourceLead sourceLead) {

    /**
     * Assembles the detail from the request, its source Order/Proposal/Opportunity/Lead and the resolved user
     * names.
     *
     * @param r the booking request aggregate (with its items loaded)
     * @param order the source Commercial Order
     * @param proposal the source Proposal
     * @param opportunity the source Opportunity
     * @param lead the source Lead
     * @param names map of user id → display name for the operator, the responsible and the creator
     * @return the detail read model
     */
    public static BookingRequestDetail from(
            BookingRequest r,
            CommercialOrder order,
            Proposal proposal,
            Opportunity opportunity,
            Lead lead,
            Map<UUID, String> names) {
        List<Item> items = r.items().stream()
                .sorted(Comparator.comparing(BookingItem::createdAt))
                .map(Item::from)
                .toList();
        long requiring = items.stream().filter(Item::requiresBooking).count();
        long confirmed = items.stream()
                .filter(i -> i.status() == BookingItemStatus.CONFIRMED)
                .count();
        long failed = items.stream()
                .filter(i -> i.status() == BookingItemStatus.FAILED)
                .count();
        return new BookingRequestDetail(
                r.id(),
                r.commercialOrderId(),
                order.number(),
                r.status(),
                r.bookingOperatorId(),
                nameOf(names, r.bookingOperatorId()),
                r.bookingOperatorId() == null,
                r.responsiblePersonId(),
                nameOf(names, r.responsiblePersonId()),
                r.notes(),
                requiring,
                confirmed,
                failed,
                r.createdAt(),
                r.updatedAt(),
                nameOf(names, r.createdBy()),
                items,
                new SourceOrder(order.id(), order.number(), order.status()),
                new SourceProposal(proposal.id(), proposal.title(), proposal.status()),
                new SourceOpportunity(opportunity.id(), opportunity.name(), opportunity.stage()),
                new SourceLead(lead.id(), lead.name()));
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    /** The source Commercial Order, kept traceable from the reservation (its number is the human identifier). */
    public record SourceOrder(UUID id, long number, CommercialOrderStatus status) {}

    /** The source Proposal (commercial reference), kept traceable from the reservation. */
    public record SourceProposal(UUID id, String title, ProposalStatus status) {}

    /** The source Opportunity (commercial reference), kept traceable from the reservation. */
    public record SourceOpportunity(UUID id, String name, OpportunityStage stage) {}

    /** The source Lead, kept traceable from the reservation. */
    public record SourceLead(UUID id, String name) {}

    /**
     * A booking line: a snapshot of a Commercial Order item, traceable to it via {@code orderItemId}, with its
     * booking classification ({@code requiresBooking}) and its current booking status (the per-item
     * confirmation/failure signal). Carries <b>no monetary data</b>.
     */
    public record Item(
            UUID id,
            UUID orderItemId,
            ProposalItemType type,
            String description,
            int quantity,
            boolean requiresBooking,
            BookingItemStatus status) {

        static Item from(BookingItem i) {
            return new Item(
                    i.id(), i.orderItemId(), i.type(), i.description(), i.quantity(), i.requiresBooking(), i.status());
        }
    }
}
