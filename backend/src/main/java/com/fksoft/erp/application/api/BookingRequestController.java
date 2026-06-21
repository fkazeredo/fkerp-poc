package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.BookingRequestResponse;
import com.fksoft.erp.application.api.dto.CreateBookingRequestRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.booking.service.BookingRequestService;
import com.fksoft.erp.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    // Source-order visibility for creation reuses the Order read tiers.
    private boolean canSeeAllOrders() {
        return userContext.hasScope("sales:order:read:all");
    }

    private boolean canSeeUnassignedOrders() {
        return userContext.hasScope("sales:order:read:unassigned");
    }
}
