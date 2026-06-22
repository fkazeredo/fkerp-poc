package com.fksoft.erp.domain.booking.model;

import com.fksoft.erp.domain.booking.exception.BookingItemAlreadyResolvedException;
import com.fksoft.erp.domain.booking.exception.BookingItemNotConfirmableException;
import com.fksoft.erp.domain.sales.model.CommercialOrderItem;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A line to be reserved within a {@link BookingRequest}: a snapshot of a {@link CommercialOrderItem} taken
 * when the request is created (type, description, quantity) plus its booking classification and status. It
 * carries <b>no monetary data</b> — a Booking Request is not financial data; the money stays on the
 * Commercial Order. The booking requirement follows the type: a travel package or a car rental always require
 * booking, a service fee never does, and an OTHER item only when the operations team explicitly marked it at
 * creation. Items that require booking start {@link BookingItemStatus#PENDING}; the others
 * {@link BookingItemStatus#NOT_REQUIRED}.
 */
@Entity
@Table(name = "booking_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingItem {

    @Id
    private UUID id;

    // The source Commercial Order item this line was snapshotted from (traceability).
    @NotNull
    @Column(name = "order_item_id", nullable = false, updatable = false)
    private UUID orderItemId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalItemType type;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false)
    private String description;

    @Min(1)
    @Column(nullable = false)
    private int quantity;

    @Column(name = "requires_booking", nullable = false)
    private boolean requiresBooking;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingItemStatus status;

    // The manual confirmation of the external reservation — populated only when the item is confirmed (null
    // otherwise). Carries no monetary data.
    @Embedded
    private BookingItemConfirmation confirmation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Snapshots a Commercial Order item into a new Booking item — copying <b>what</b> to reserve (type,
     * description, quantity), classifying its booking need (see {@link #requiresBooking(ProposalItemType,
     * boolean)}), and setting the initial status (PENDING when it requires booking, NOT_REQUIRED otherwise).
     * No monetary data is copied.
     *
     * @param source the source Commercial Order item (untouched)
     * @param explicitlyRequired whether the operations team explicitly marked this item as requiring booking
     *     (only meaningful for an OTHER item)
     * @return a new Booking item
     */
    static BookingItem snapshotOf(CommercialOrderItem source, boolean explicitlyRequired) {
        BookingItem item = new BookingItem();
        item.id = UUID.randomUUID();
        item.orderItemId = source.id();
        item.type = source.type();
        item.description = source.description();
        item.quantity = source.quantity();
        item.requiresBooking = requiresBooking(source.type(), explicitlyRequired);
        item.status = item.requiresBooking ? BookingItemStatus.PENDING : BookingItemStatus.NOT_REQUIRED;
        return item;
    }

    /**
     * Whether an order item of the given type requires booking: a travel package or a car rental always do, a
     * service fee never does, and an OTHER item only when explicitly marked as booking-required.
     *
     * @param type the order item type
     * @param explicitlyRequired whether an OTHER item was explicitly marked as requiring booking
     * @return {@code true} if the item requires a booking operation
     */
    static boolean requiresBooking(ProposalItemType type, boolean explicitlyRequired) {
        return switch (type) {
            case TRAVEL_PACKAGE, CAR_RENTAL -> true;
            case SERVICE_FEE -> false;
            case OTHER -> explicitlyRequired;
        };
    }

    /**
     * Manually confirms this item's external reservation through the Travel Package flow: records the
     * confirmation and moves the item to {@link BookingItemStatus#CONFIRMED}. Only a Travel Package item that
     * requires booking and is not already confirmed/cancelled can be confirmed; the item protects its own
     * invariant. No monetary/Financial/Commission/Customer Care data is created and no external call is made.
     *
     * @param confirmation the external reservation result (system, locator, date, author + optional metadata)
     * @throws BookingItemNotConfirmableException if this is not a Travel Package item that requires booking
     * @throws BookingItemAlreadyResolvedException if this item is already confirmed or cancelled
     */
    void confirmTravelPackage(BookingItemConfirmation confirmation) {
        if (type != ProposalItemType.TRAVEL_PACKAGE || !requiresBooking) {
            throw new BookingItemNotConfirmableException();
        }
        if (status == BookingItemStatus.CONFIRMED || status == BookingItemStatus.CANCELLED) {
            throw new BookingItemAlreadyResolvedException();
        }
        this.confirmation = confirmation;
        this.status = BookingItemStatus.CONFIRMED;
    }
}
