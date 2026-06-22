package com.fksoft.erp.domain.booking.model;

import com.fksoft.erp.domain.booking.exception.BookingItemNotFoundException;
import com.fksoft.erp.domain.booking.exception.BookingItemNotMarkableException;
import com.fksoft.erp.domain.booking.exception.CommercialOrderNotPendingBookingException;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderItem;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A Booking Request: the operational record that starts the (still manual) reservation process for a
 * Commercial Order that is {@link CommercialOrderStatus#PENDING_BOOKING}. Aggregate root of the Booking
 * Operations context ({@code domain.booking}). It preserves the source Commercial Order / Proposal /
 * Opportunity / Lead references and the commercial responsible, and snapshots <b>what</b> must be reserved
 * (the booking items, classified by booking need) — carrying <b>no monetary data</b>. A Booking Request is
 * NOT an external integration, a Receivable, a Payment, a Commission or Customer Care. It starts
 * {@link BookingRequestStatus#PENDING}; the operational transitions (attempts, confirmation, failure) are
 * later slices.
 */
@Entity
@Table(name = "booking_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingRequest {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    // The source Commercial Order this request fulfills (preserved; the Order is not modified here).
    @NotNull
    @Column(name = "commercial_order_id", nullable = false, updatable = false)
    private UUID commercialOrderId;

    // Source references, denormalized from the Order for traceability.
    @NotNull
    @Column(name = "proposal_id", nullable = false, updatable = false)
    private UUID proposalId;

    @NotNull
    @Column(name = "opportunity_id", nullable = false, updatable = false)
    private UUID opportunityId;

    @NotNull
    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    // The commercial responsible, preserved from the Order (may be null).
    @Column(name = "responsible_person_id")
    private UUID responsiblePersonId;

    // The assigned booking operator (optional initially; assignment is a later slice).
    @Column(name = "booking_operator_id")
    private UUID bookingOperatorId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingRequestStatus status;

    @Size(max = 2000)
    private String notes;

    // The lines to reserve — a snapshot of the Order's items, classified by booking need.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "booking_request_id", nullable = false)
    private List<BookingItem> items = new ArrayList<>();

    // The manual booking-attempt history (part of the aggregate): append-only operational log.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "booking_request_id", nullable = false)
    private List<BookingAttempt> attempts = new ArrayList<>();

    // The most recent attempt instant, denormalized from the attempt history so the list shows the latest
    // attempt without an N+1 query (null until the first attempt is registered).
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    // The latest manual attempt's planned next-action date, denormalized (mirrors lastAttemptAt) so the pending
    // worklist can flag an overdue next action without an N+1 / subquery. Null when none is planned.
    @Column(name = "next_action_date")
    private LocalDate nextActionDate;

    // The instant the request first reached CONFIRMED, denormalized so the indicators can compute the average
    // creation→confirmation time without scanning the items. Null until the request is fully confirmed.
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

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
     * Creates a Booking Request from a Commercial Order that is PENDING_BOOKING, snapshotting its items
     * (classified by booking need) and preserving the source references and the commercial responsible. The
     * request starts {@link BookingRequestStatus#PENDING}. Creates no external reservation and no Receivable,
     * Payment or Commission data.
     *
     * @param order the source Commercial Order; must be {@link CommercialOrderStatus#PENDING_BOOKING}
     * @param bookingOperatorId the assigned booking operator, or {@code null} (optional initially)
     * @param notes optional booking notes
     * @param bookingRequiredItemIds ids of the Order's OTHER items to mark as requiring booking (each must be
     *     an OTHER item of the Order); {@code null}/empty marks none
     * @param createdBy id of the user creating the request
     * @return a new, unsaved Booking Request
     * @throws CommercialOrderNotPendingBookingException if the Order is not PENDING_BOOKING
     * @throws BookingItemNotMarkableException if a marked id is not an OTHER item of the Order
     */
    public static BookingRequest createFromOrder(
            CommercialOrder order,
            UUID bookingOperatorId,
            String notes,
            Set<UUID> bookingRequiredItemIds,
            UUID createdBy) {
        if (order.status() != CommercialOrderStatus.PENDING_BOOKING) {
            throw new CommercialOrderNotPendingBookingException();
        }
        Set<UUID> required = bookingRequiredItemIds == null ? Set.of() : bookingRequiredItemIds;
        // Only OTHER items of the Order may be explicitly marked as requiring booking (a travel package / car
        // rental already require it; a service fee never does). Reject any other marked id (incl. unknown ones).
        Map<UUID, ProposalItemType> typeById =
                order.items().stream().collect(Collectors.toMap(CommercialOrderItem::id, CommercialOrderItem::type));
        for (UUID id : required) {
            if (typeById.get(id) != ProposalItemType.OTHER) {
                throw new BookingItemNotMarkableException(id);
            }
        }
        BookingRequest request = new BookingRequest();
        request.id = UUID.randomUUID();
        request.commercialOrderId = order.id();
        request.proposalId = order.proposalId();
        request.opportunityId = order.opportunityId();
        request.leadId = order.leadId();
        request.responsiblePersonId = order.responsiblePersonId();
        request.bookingOperatorId = bookingOperatorId;
        request.notes = notes;
        request.status = BookingRequestStatus.PENDING;
        order.items().forEach(item -> request.items.add(BookingItem.snapshotOf(item, required.contains(item.id()))));
        request.createdBy = createdBy;
        request.updatedBy = createdBy;
        return request;
    }

    /**
     * Registers a manual booking attempt (append-only operational history) and refreshes the denormalized
     * latest-attempt instant. When the attempt references a booking item, it must belong to this request.
     * Registering an attempt moves the request from {@link BookingRequestStatus#PENDING} to
     * {@link BookingRequestStatus#IN_PROGRESS}; it never changes a booking item's status, never confirms the
     * booking, and never creates Financial or Commission data.
     *
     * @param bookingItemId the booking item this attempt concerns, or {@code null} for the whole request
     * @param type the attempt type
     * @param result the attempt outcome (recorded for history only — even {@code FAILED} does not fail the
     *     reservation)
     * @param description what was done
     * @param occurredAt when the attempt happened
     * @param nextActionDate optional planned next action date
     * @param byUser id of the user registering the attempt (its author)
     * @throws BookingItemNotFoundException if {@code bookingItemId} is given but is not an item of this request
     */
    public void recordAttempt(
            UUID bookingItemId,
            BookingAttemptType type,
            BookingAttemptResult result,
            String description,
            Instant occurredAt,
            LocalDate nextActionDate,
            UUID byUser) {
        if (bookingItemId != null && items.stream().noneMatch(item -> item.id().equals(bookingItemId))) {
            throw new BookingItemNotFoundException();
        }
        attempts.add(BookingAttempt.of(bookingItemId, type, result, description, occurredAt, nextActionDate, byUser));
        // Track the latest attempt's instant and its planned next action (the most recent attempt wins, so an
        // older attempt registered later does not override the current next action).
        if (lastAttemptAt == null || occurredAt.isAfter(lastAttemptAt)) {
            lastAttemptAt = occurredAt;
            this.nextActionDate = nextActionDate;
        }
        consolidateStatus();
        updatedBy = byUser;
    }

    /**
     * Manually confirms a Travel Package booking item's external reservation and consolidates the request
     * status. The item must belong to this request and be a Travel Package item that requires booking and is
     * not already resolved (the item enforces that). After confirming, the request status rolls up: when every
     * item that requires booking is confirmed the request becomes {@link BookingRequestStatus#CONFIRMED},
     * otherwise (some but not all confirmed) {@link BookingRequestStatus#PARTIALLY_CONFIRMED}. No external call
     * is made and no Financial/Commission/Customer Care data is created.
     *
     * @param itemId the booking item to confirm
     * @param confirmation the external reservation result
     * @param byUser the user confirming (the request's updater)
     * @throws BookingItemNotFoundException if the item is not part of this request
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemNotConfirmableException if the item is not a
     *     Travel Package item that requires booking
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemAlreadyResolvedException if the item is already
     *     confirmed or cancelled
     */
    public void confirmTravelPackageItem(UUID itemId, BookingItemConfirmation confirmation, UUID byUser) {
        item(itemId).confirmTravelPackage(confirmation);
        consolidateStatus();
        updatedBy = byUser;
    }

    /**
     * Manually confirms a Car Rental booking item's external reservation and consolidates the request status.
     * The item must belong to this request and be a Car Rental item that requires booking and is not already
     * resolved (the item enforces that). After confirming, the request status rolls up the same way as the
     * Travel Package flow. No external call is made and no Financial/Commission/Customer Care data is created.
     *
     * @param itemId the booking item to confirm
     * @param confirmation the external reservation result
     * @param byUser the user confirming (the request's updater)
     * @throws BookingItemNotFoundException if the item is not part of this request
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemNotConfirmableException if the item is not a Car
     *     Rental item that requires booking
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemAlreadyResolvedException if the item is already
     *     confirmed or cancelled
     */
    public void confirmCarRentalItem(UUID itemId, BookingItemConfirmation confirmation, UUID byUser) {
        item(itemId).confirmCarRental(confirmation);
        consolidateStatus();
        updatedBy = byUser;
    }

    /**
     * Manually marks a booking item as failed and consolidates the request status. The item must belong to this
     * request, require booking and not be already resolved (the item enforces that). After failing, the request
     * status rolls up: when items requiring booking are confirmed it is {@link BookingRequestStatus#CONFIRMED}
     * (all) or {@link BookingRequestStatus#PARTIALLY_CONFIRMED} (some); when none is confirmed but one failed it
     * is {@link BookingRequestStatus#FAILED}. The failed item stays visible and may later be retried/confirmed.
     * The Commercial Order is not cancelled and no Financial/Commission/Customer Care data is created.
     *
     * @param itemId the booking item to fail
     * @param failure the failure record (reason, optional note, author, date)
     * @param byUser the user marking the failure (the request's updater)
     * @throws BookingItemNotFoundException if the item is not part of this request
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemNotFailableException if the item does not require
     *     booking
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemAlreadyResolvedException if the item is already
     *     confirmed or cancelled
     */
    public void failBookingItem(UUID itemId, BookingItemFailure failure, UUID byUser) {
        item(itemId).fail(failure);
        consolidateStatus();
        updatedBy = byUser;
    }

    private BookingItem item(UUID itemId) {
        return items.stream()
                .filter(i -> i.id().equals(itemId))
                .findFirst()
                .orElseThrow(BookingItemNotFoundException::new);
    }

    /**
     * Consolidates the request status from the items that require booking and the attempt history (purely
     * state-derived; an explicitly {@link BookingRequestStatus#CANCELLED} request is never overridden):
     *
     * <ul>
     *   <li>every requiring item confirmed → {@link BookingRequestStatus#CONFIRMED};
     *   <li>at least one (but not all) requiring item confirmed → {@link BookingRequestStatus#PARTIALLY_CONFIRMED};
     *   <li>none confirmed but at least one requiring item failed → {@link BookingRequestStatus#FAILED} (the
     *       operation cannot proceed until the failure is retried);
     *   <li>nothing confirmed or failed yet but at least one attempt exists → {@link BookingRequestStatus#IN_PROGRESS};
     *   <li>otherwise (all pending, no attempt) → {@link BookingRequestStatus#PENDING}.
     * </ul>
     *
     * Confirming a previously failed item reconsolidates the request (FAILED → PARTIALLY_CONFIRMED/CONFIRMED).
     */
    private void consolidateStatus() {
        if (status == BookingRequestStatus.CANCELLED) {
            return;
        }
        long requiring = items.stream().filter(BookingItem::requiresBooking).count();
        long confirmed = items.stream()
                .filter(BookingItem::requiresBooking)
                .filter(i -> i.status() == BookingItemStatus.CONFIRMED)
                .count();
        long failed = items.stream()
                .filter(BookingItem::requiresBooking)
                .filter(i -> i.status() == BookingItemStatus.FAILED)
                .count();
        if (requiring > 0 && confirmed == requiring) {
            status = BookingRequestStatus.CONFIRMED;
            // Stamp the first time the request reaches CONFIRMED (feeds the average creation→confirmation metric).
            if (confirmedAt == null) {
                confirmedAt = Instant.now();
            }
        } else if (confirmed > 0) {
            status = BookingRequestStatus.PARTIALLY_CONFIRMED;
        } else if (failed > 0) {
            status = BookingRequestStatus.FAILED;
        } else if (!attempts.isEmpty()) {
            status = BookingRequestStatus.IN_PROGRESS;
        } else {
            status = BookingRequestStatus.PENDING;
        }
    }
}
