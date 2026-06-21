package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.BookingRequestListParams;
import com.fksoft.erp.application.api.dto.BookingRequestResponse;
import com.fksoft.erp.application.api.dto.CreateBookingRequestRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.booking.service.BookingRequestService;
import com.fksoft.erp.domain.booking.service.data.BookingRequestDetail;
import com.fksoft.erp.domain.booking.service.data.BookingRequestListItem;
import com.fksoft.erp.domain.booking.service.data.BookingRequestSearchCriteria;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
