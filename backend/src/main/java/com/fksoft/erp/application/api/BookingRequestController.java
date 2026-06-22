package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.BookingRequestListParams;
import com.fksoft.erp.application.api.dto.BookingRequestResponse;
import com.fksoft.erp.application.api.dto.ConfirmCarRentalRequest;
import com.fksoft.erp.application.api.dto.ConfirmTravelPackageRequest;
import com.fksoft.erp.application.api.dto.CreateBookingRequestRequest;
import com.fksoft.erp.application.api.dto.FailBookingItemRequest;
import com.fksoft.erp.application.api.dto.RegisterBookingAttemptRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.booking.service.BookingRequestService;
import com.fksoft.erp.domain.booking.service.data.BookingIndicators;
import com.fksoft.erp.domain.booking.service.data.BookingRequestDetail;
import com.fksoft.erp.domain.booking.service.data.BookingRequestListItem;
import com.fksoft.erp.domain.booking.service.data.BookingRequestSearchCriteria;
import com.fksoft.erp.domain.booking.service.data.ConfirmCarRentalCommand;
import com.fksoft.erp.domain.booking.service.data.ConfirmTravelPackageCommand;
import com.fksoft.erp.domain.booking.service.data.FailBookingItemCommand;
import com.fksoft.erp.domain.booking.service.data.PendingBookingRequest;
import com.fksoft.erp.domain.booking.service.data.RecordBookingAttemptCommand;
import com.fksoft.erp.infra.security.UserContextProvider;
import com.fksoft.erp.infra.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Booking Request endpoints (Booking Operations). Creating a Booking Request from a PENDING_BOOKING
 * Commercial Order requires {@code booking:request:create}, and the caller must be allowed to see the source
 * Order (the Order read tiers are reused to decide that). Creating a request contacts no external system and
 * creates no Receivable, Payment or Commission data.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingRequestController {

    private final BookingRequestService bookingService;
    private final UserContextProvider userContext;

    /**
     * Creates a Booking Request from a PENDING_BOOKING Commercial Order (the request starts PENDING).
     *
     * @param request the source order id plus the optional operator and notes
     * @return 201 Created with the new Booking Request id and status
     */
    @PostMapping
    public ResponseEntity<BookingRequestResponse> create(@Valid @RequestBody CreateBookingRequestRequest request) {
        UUID id = bookingService.create(
                request.commercialOrderId(),
                request.bookingOperatorId(),
                request.notes(),
                request.bookingRequiredItemIds(),
                userContext.currentUserId(),
                canSeeAllOrders(),
                canSeeUnassignedOrders());
        return ResponseEntity.created(URI.create("/api/bookings/" + id))
                .body(new BookingRequestResponse(id, BookingRequestStatus.PENDING));
    }

    /**
     * Operational, paginated list of Booking Requests visible to the caller, with optional filters. The
     * terminal CONFIRMED and CANCELLED requests are excluded unless the {@code status} filter includes them;
     * FAILED stays visible by default. The contract carries operational reservation data only — never
     * Financial, Payment or Commission data.
     *
     * @param params the optional filters (see {@link BookingRequestListParams})
     * @param pageable page, size and sort (default: updatedAt desc, size 20)
     * @return a page of Booking Request list items
     */
    @GetMapping
    public PageResponse<BookingRequestListItem> list(
            BookingRequestListParams params,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        boolean operatorUnassignedOnly = "unassigned".equalsIgnoreCase(params.operator());
        UUID operatorId = (!operatorUnassignedOnly
                        && params.operator() != null
                        && !params.operator().isBlank())
                ? UUID.fromString(params.operator())
                : null;
        BookingRequestSearchCriteria criteria = new BookingRequestSearchCriteria(
                params.status(),
                operatorId,
                operatorUnassignedOnly,
                params.responsible(),
                toStartOfDayUtc(params.createdFrom()),
                toStartOfDayUtc(params.createdTo() != null ? params.createdTo().plusDays(1) : null),
                params.order(),
                params.itemType(),
                params.hasFailedItems());
        return PageResponse.from(
                bookingService.list(
                        criteria,
                        pageable,
                        userContext.currentUserId(),
                        canSeeAllBookings(),
                        canSeeUnassignedBookings()),
                item -> item);
    }

    /**
     * Operational pending-items worklist: the Booking Requests visible to the caller that need action, each
     * tagged with its reasons (unassigned operator, pending without attempt, in progress without a recent
     * attempt, a failed item, a requiring-booking item still pending, partially confirmed, or an overdue next
     * action). Gated by the Booking read tiers (the policy narrows visibility at the query level, like the list).
     * It is operational, not an executive dashboard: read-only, with no notification/SLA engine and no external
     * retry; the contract carries operational reservation data only — never Financial, Payment or Commission data.
     *
     * @param pageable page, size and sort (default: updatedAt desc, size 20)
     * @return a page of pending Booking Request items
     */
    @GetMapping("/pending")
    public PageResponse<PendingBookingRequest> pending(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(
                bookingService.pending(
                        pageable, userContext.currentUserId(), canSeeAllBookings(), canSeeUnassignedBookings()),
                item -> item);
    }

    /**
     * Minimum Booking Operations indicators over the requests visible to the caller: the volume figures (total,
     * by status, items by type, failed items, average creation→confirmation time) over the requested period, plus
     * a current snapshot of the requests ready for Financial Operations (CONFIRMED). Gated by the Booking read
     * tiers (the policy narrows visibility). Operational, not an executive dashboard: read-only, no Financial,
     * Payment, Commission or external-integration data.
     *
     * @param createdFrom optional inclusive lower bound on the creation date (ISO date)
     * @param createdTo optional inclusive upper bound on the creation date (ISO date)
     * @return the indicators
     */
    @GetMapping("/indicators")
    public BookingIndicators indicators(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo) {
        return bookingService.indicators(
                userContext.currentUserId(),
                canSeeAllBookings(),
                canSeeUnassignedBookings(),
                toStartOfDayUtc(createdFrom),
                toStartOfDayUtc(createdTo != null ? createdTo.plusDays(1) : null));
    }

    /**
     * Full detail of a Booking Request the caller may see, with the source Commercial Order, Proposal,
     * Opportunity and Lead kept traceable and each booking item carrying its status (the per-item
     * confirmation/failure signal). The contract carries operational reservation data only — never Financial,
     * Payment or Commission data.
     *
     * @param id the booking request id
     * @return the Booking Request detail read model
     */
    @GetMapping("/{id}")
    public BookingRequestDetail detail(@PathVariable UUID id) {
        return bookingService.detail(id, userContext.currentUserId(), canSeeAllBookings(), canSeeUnassignedBookings());
    }

    /**
     * Registers a manual booking attempt on a Booking Request (append-only operational history) and returns the
     * refreshed detail. Requires {@code booking:request:update} and that the caller may see the request.
     * Registering an attempt may move the request from PENDING to IN_PROGRESS; it never confirms the booking,
     * never changes a booking item's status, and never creates Financial or Commission data.
     *
     * @param id the booking request id
     * @param request the attempt data (optional item link, type, result, description, date, optional next action)
     * @return the updated Booking Request detail
     */
    @PostMapping("/{id}/attempts")
    public BookingRequestDetail registerAttempt(
            @PathVariable UUID id, @Valid @RequestBody RegisterBookingAttemptRequest request) {
        RecordBookingAttemptCommand command = new RecordBookingAttemptCommand(
                request.bookingItemId(),
                request.type(),
                request.result(),
                request.description(),
                request.occurredAt(),
                request.nextActionDate());
        return bookingService.recordAttempt(
                id, command, userContext.currentUserId(), canSeeAllBookings(), canSeeUnassignedBookings());
    }

    /**
     * Manually confirms a Travel Package booking item's external reservation and returns the refreshed detail.
     * Requires {@code booking:request:update} and that the caller may see the request. Records the external
     * reservation result on the item, moves it to CONFIRMED and consolidates the request status; it calls no
     * external system and creates no Financial, Payment, Commission or Customer Care data.
     *
     * @param id the booking request id
     * @param itemId the booking item to confirm
     * @param request the confirmation data (external system + locator + date + optional travel metadata)
     * @return the updated Booking Request detail
     */
    @PostMapping("/{id}/items/{itemId}/confirm")
    public BookingRequestDetail confirmTravelPackageItem(
            @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody ConfirmTravelPackageRequest request) {
        ConfirmTravelPackageCommand command = new ConfirmTravelPackageCommand(
                request.externalSystem(),
                request.externalLocator(),
                request.confirmedAt(),
                request.packageDescription(),
                request.travelStartDate(),
                request.travelEndDate(),
                request.travelerNotes(),
                request.operationalNotes());
        return bookingService.confirmTravelPackageItem(
                id, itemId, command, userContext.currentUserId(), canSeeAllBookings(), canSeeUnassignedBookings());
    }

    /**
     * Manually confirms a Car Rental booking item's external reservation and returns the refreshed detail.
     * Requires {@code booking:request:update} and that the caller may see the request. Records the external
     * reservation result on the item, moves it to CONFIRMED and consolidates the request status; it calls no
     * external system and creates no Financial, Payment, Commission or Customer Care data.
     *
     * @param id the booking request id
     * @param itemId the booking item to confirm
     * @param request the confirmation data (external system + locator + date + optional car-rental metadata)
     * @return the updated Booking Request detail
     */
    @PostMapping("/{id}/items/{itemId}/confirm-car-rental")
    public BookingRequestDetail confirmCarRentalItem(
            @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody ConfirmCarRentalRequest request) {
        ConfirmCarRentalCommand command = new ConfirmCarRentalCommand(
                request.externalSystem(),
                request.externalLocator(),
                request.confirmedAt(),
                request.rentalCompany(),
                request.pickupLocation(),
                request.dropoffLocation(),
                request.pickupAt(),
                request.dropoffAt(),
                request.carCategory(),
                request.operationalNotes());
        return bookingService.confirmCarRentalItem(
                id, itemId, command, userContext.currentUserId(), canSeeAllBookings(), canSeeUnassignedBookings());
    }

    /**
     * Marks a booking item as failed and returns the refreshed detail. Requires {@code booking:request:update}
     * and that the caller may see the request. Records the failure on the item, moves it to FAILED and
     * consolidates the request status; the failed item stays visible and may later be retried/confirmed. It
     * does not cancel the Commercial Order and creates no Financial, Payment, Commission or Customer Care data.
     *
     * @param id the booking request id
     * @param itemId the booking item to fail
     * @param request the failure data (reason, optional note, date)
     * @return the updated Booking Request detail
     */
    @PostMapping("/{id}/items/{itemId}/fail")
    public BookingRequestDetail failBookingItem(
            @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody FailBookingItemRequest request) {
        FailBookingItemCommand command =
                new FailBookingItemCommand(request.failureReason(), request.failureNote(), request.failedAt());
        return bookingService.failBookingItem(
                id, itemId, command, userContext.currentUserId(), canSeeAllBookings(), canSeeUnassignedBookings());
    }

    // Source-order visibility for creation reuses the Order read tiers.
    private boolean canSeeAllOrders() {
        return userContext.hasScope("sales:order:read:all");
    }

    private boolean canSeeUnassignedOrders() {
        return userContext.hasScope("sales:order:read:unassigned");
    }

    // Booking listing uses the Booking read tiers.
    private boolean canSeeAllBookings() {
        return userContext.hasScope("booking:request:read:all");
    }

    private boolean canSeeUnassignedBookings() {
        return userContext.hasScope("booking:request:read:unassigned");
    }

    // The creation period is given as calendar dates; the column is an instant, so anchor at UTC midnight
    // (the upper bound is pre-incremented by a day, making the range [from, to) exclusive).
    private static Instant toStartOfDayUtc(LocalDate date) {
        return date != null ? date.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }
}
