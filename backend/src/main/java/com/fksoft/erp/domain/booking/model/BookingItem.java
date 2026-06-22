package com.fksoft.erp.domain.booking.model;

import com.fksoft.erp.domain.booking.exception.BookingItemAlreadyResolvedException;
import com.fksoft.erp.domain.booking.exception.BookingItemNotConfirmableException;
import com.fksoft.erp.domain.booking.exception.BookingItemNotFailableException;
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
 * creation. Items that require booking start {@code PENDING}; the others
 * {@code NOT_REQUIRED}.
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

    // Denormalized status code (mirrors a 'booking_item' workflow state); the current_state_id FK is kept in
    // sync from this code by a DB trigger. The item's status is computed by its own transitions (it is never a
    // user-chosen target), so the code stays the entity's source value and the FK is derived (data-driven storage).
    @NotBlank
    @Size(max = 60)
    @Column(nullable = false)
    private String status;

    // The manual confirmation of the external reservation — populated only when the item is confirmed (null
    // otherwise). Carries no monetary data.
    @Embedded
    private BookingItemConfirmation confirmation;

    // The manual failure — populated only when the item is marked as failed (the current/last failure; null
    // otherwise). Carries no monetary data.
    @Embedded
    private BookingItemFailure failure;

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
        item.status = item.requiresBooking ? "PENDING" : "NOT_REQUIRED";
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
     * confirmation and moves the item to {@code CONFIRMED}. Only a Travel Package item that
     * requires booking and is not already confirmed/cancelled can be confirmed; the item protects its own
     * invariant. No monetary/Financial/Commission/Customer Care data is created and no external call is made.
     *
     * @param confirmation the external reservation result (system, locator, date, author + optional metadata)
     * @throws BookingItemNotConfirmableException if this is not a Travel Package item that requires booking
     * @throws BookingItemAlreadyResolvedException if this item is already confirmed or cancelled
     */
    void confirmTravelPackage(BookingItemConfirmation confirmation) {
        confirm(confirmation, ProposalItemType.TRAVEL_PACKAGE);
    }

    /**
     * Manually confirms this item's external reservation through the Car Rental flow: records the confirmation
     * and moves the item to {@code CONFIRMED}. Only a Car Rental item that requires booking
     * and is not already confirmed/cancelled can be confirmed. No monetary/Financial/Commission/Customer Care
     * data is created and no external call is made.
     *
     * @param confirmation the external reservation result (system, locator, date, author + optional metadata)
     * @throws BookingItemNotConfirmableException if this is not a Car Rental item that requires booking
     * @throws BookingItemAlreadyResolvedException if this item is already confirmed or cancelled
     */
    void confirmCarRental(BookingItemConfirmation confirmation) {
        confirm(confirmation, ProposalItemType.CAR_RENTAL);
    }

    // Shared confirmation guard + transition: only an item of the expected type that requires booking and is not
    // already resolved can be confirmed (the item protects its own invariant, §5.3).
    private void confirm(BookingItemConfirmation confirmation, ProposalItemType expectedType) {
        if (type != expectedType || !requiresBooking) {
            throw new BookingItemNotConfirmableException();
        }
        if ("CONFIRMED".equals(status) || "CANCELLED".equals(status)) {
            throw new BookingItemAlreadyResolvedException();
        }
        this.confirmation = confirmation;
        this.status = "CONFIRMED";
    }

    /**
     * Manually marks this item as failed: records the failure and moves the item to
     * {@code FAILED}. Only an item that requires booking and is not already
     * confirmed/cancelled can be failed; a failed item stays visible as an operational problem and may later
     * receive new attempts or be confirmed (the failure does not bar a retry). No external call is made and no
     * Financial/Commission/Customer Care data is created; the Commercial Order is not cancelled.
     *
     * @param failure the failure record (reason, optional note, author, date)
     * @throws BookingItemNotFailableException if this item does not require booking
     * @throws BookingItemAlreadyResolvedException if this item is already confirmed or cancelled
     */
    void fail(BookingItemFailure failure) {
        if (!requiresBooking) {
            throw new BookingItemNotFailableException();
        }
        if ("CONFIRMED".equals(status) || "CANCELLED".equals(status)) {
            throw new BookingItemAlreadyResolvedException();
        }
        this.failure = failure;
        this.status = "FAILED";
    }
}
