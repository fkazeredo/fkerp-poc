package com.fksoft.erp.domain.sales.model;

import com.fksoft.erp.domain.sales.exception.ProposalNotAcceptedException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
 * A Commercial Order: the formal internal record of a closed commercial deal, created from an
 * {@code ACCEPTED} Proposal. It is the aggregate root of its own slice in the Sales &amp;
 * Proposals context ({@code domain.sales}). It is a faithful <b>snapshot</b> of the source Proposal at
 * acceptance time — the items, the subtotal/total, and the source Proposal / Opportunity / Lead references
 * and the responsible person are preserved and never recomputed. Creating the Order does NOT create any
 * Booking, Receivable, Payment, Commission or Customer Care data; the booking is only flagged as pending
 * (the {@code PENDING_BOOKING} / {@code BOOKING_NOT_REQUIRED} states).
 */
@Entity
@Table(name = "commercial_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialOrder {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    // A human-friendly sequential number (assigned from a DB sequence at creation; rendered as PC-000n in the
    // UI). Unique and immutable.
    @NotNull
    @Column(nullable = false, updatable = false, unique = true)
    private Long number;

    // The source Proposal this Order was created from (preserved; the Proposal is not modified).
    @NotNull
    @Column(name = "proposal_id", nullable = false, updatable = false)
    private UUID proposalId;

    // The source Opportunity and Lead, kept for traceability (denormalized from the Proposal).
    @NotNull
    @Column(name = "opportunity_id", nullable = false, updatable = false)
    private UUID opportunityId;

    @NotNull
    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @Column(name = "responsible_person_id")
    private UUID responsiblePersonId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private CommercialOrderStatus status;

    // The consolidated booking status code, reflected from the Booking Operations context (Sales owns this column;
    // it is set by a Sales event listener, never by Booking). Null until a Booking Request exists for this Order.
    // This is a read-only reflection — it never drives the Order's own lifecycle ({@link #status}).
    @Size(max = 60)
    @Column(name = "booking_status")
    private String bookingStatus;

    // The order lines — an immutable snapshot of the Proposal's items at creation time.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<CommercialOrderItem> items = new ArrayList<>();

    // The items subtotal and the order total, snapshotted from the Proposal (never recomputed here).
    @NotNull
    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @NotNull
    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

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
     * Creates a Commercial Order from an Accepted Proposal, snapshotting its items, totals and source
     * references. The Order starts at {@code PENDING_BOOKING} when it contains at least one item that requires
     * booking (a travel package or a car rental), or {@code BOOKING_NOT_REQUIRED} otherwise. No Booking,
     * Receivable, Payment or Commission data is created.
     *
     * @param proposal the source Proposal; must be {@code ACCEPTED}
     * @param createdBy id of the user creating the Order
     * @param number the sequential order number (assigned from the order-number sequence)
     * @return a new, unsaved Commercial Order
     * @throws ProposalNotAcceptedException if the Proposal is not Accepted
     */
    public static CommercialOrder createFromProposal(Proposal proposal, UUID createdBy, long number) {
        if (proposal.status() != ProposalStatus.ACCEPTED) {
            throw new ProposalNotAcceptedException();
        }
        CommercialOrder order = new CommercialOrder();
        order.id = UUID.randomUUID();
        order.number = number;
        order.proposalId = proposal.id();
        order.opportunityId = proposal.opportunityId();
        order.leadId = proposal.leadId();
        order.responsiblePersonId = proposal.responsiblePersonId();
        proposal.items().forEach(item -> order.items.add(CommercialOrderItem.snapshotOf(item)));
        order.subtotal = proposal.subtotal();
        order.total = proposal.total();
        order.status = order.requiresBooking()
                ? CommercialOrderStatus.PENDING_BOOKING
                : CommercialOrderStatus.BOOKING_NOT_REQUIRED;
        order.createdBy = createdBy;
        order.updatedBy = createdBy;
        return order;
    }

    /**
     * Whether the Order is still active (not cancelled).
     *
     * @return {@code true} unless the Order is cancelled
     */
    public boolean isActive() {
        return !"CANCELLED".equals(status);
    }

    /**
     * Reflects the consolidated booking status from the Booking Operations context onto this Order. This is a
     * read-only reflection the Order <b>owns</b> (Booking never writes the Order): it records whether the sale is
     * still awaiting reservation, partially confirmed, confirmed or has a booking problem, so the Order is
     * identifiable (e.g. as ready for Financial Operations when {@code CONFIRMED}). It never changes the Order's
     * own lifecycle {@link #status}, never cancels the Order, and creates no Receivable, Payment or Commission data.
     *
     * @param bookingStatus the consolidated Booking Request status code to reflect
     */
    public void reflectBookingStatus(String bookingStatus) {
        this.bookingStatus = bookingStatus;
    }

    // The Order requires booking when at least one of its items is a bookable travel product.
    private boolean requiresBooking() {
        return items.stream().anyMatch(CommercialOrderItem::requiresBooking);
    }
}
