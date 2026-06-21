package com.fksoft.erp.domain.booking.service;

import com.fksoft.erp.domain.booking.exception.BookingOperatorNotFoundException;
import com.fksoft.erp.domain.booking.exception.BookingRequestAlreadyExistsException;
import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestCreated;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.booking.repository.BookingRequestRepository;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.sales.exception.CommercialOrderAccessDeniedException;
import com.fksoft.erp.domain.sales.exception.CommercialOrderNotFoundException;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.service.OrderAccessPolicy;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service for Booking Operations: creates a Booking Request from a Commercial Order that is
 * PENDING_BOOKING, starting the (still manual) reservation process. Creating a request contacts no external
 * system and creates no Receivable, Payment or Commission data.
 */
@Service
@RequiredArgsConstructor
public class BookingRequestService {

    // A Booking Request counts against the "one active request per Order" rule while it is not cancelled.
    private static final Set<BookingRequestStatus> ACTIVE_STATUSES = BookingRequestStatus.activeStatuses();

    private final BookingRequestRepository bookingRequests;
    private final CommercialOrderRepository orders;
    private final OrderAccessPolicy orderAccessPolicy;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    /**
     * Creates a Booking Request from a Commercial Order the caller is allowed to see and that is
     * PENDING_BOOKING. A Commercial Order has at most one active Booking Request. The request preserves the
     * source references and the commercial responsible and snapshots the items to reserve (classified by
     * booking need); it starts PENDING. No external reservation and no Receivable/Payment/Commission data is
     * created.
     *
     * @param commercialOrderId the source Commercial Order id
     * @param bookingOperatorId the assigned booking operator, or {@code null} (optional initially)
     * @param notes optional booking notes
     * @param userId the authenticated user
     * @param canSeeAllOrders whether the caller may see every Commercial Order
     * @param canSeeUnassignedOrders whether the caller may also see the unassigned Order pool
     * @return the new Booking Request id
     * @throws CommercialOrderNotFoundException if the source Order does not exist
     * @throws CommercialOrderAccessDeniedException if the caller may not see the source Order
     * @throws BookingRequestAlreadyExistsException if the Order already has an active Booking Request
     * @throws BookingOperatorNotFoundException if a booking operator is given but unknown/inactive
     * @throws com.fksoft.erp.domain.booking.exception.CommercialOrderNotPendingBookingException if the Order
     *     is not PENDING_BOOKING
     */
    @Transactional
    public UUID create(
            UUID commercialOrderId,
            UUID bookingOperatorId,
            String notes,
            UUID userId,
            boolean canSeeAllOrders,
            boolean canSeeUnassignedOrders) {
        CommercialOrder order = orders.findById(commercialOrderId).orElseThrow(CommercialOrderNotFoundException::new);
        if (!orderAccessPolicy.canSee(order, userId, canSeeAllOrders, canSeeUnassignedOrders)) {
            throw new CommercialOrderAccessDeniedException();
        }
        bookingRequests
                .findFirstByCommercialOrderIdAndStatusIn(commercialOrderId, ACTIVE_STATUSES)
                .ifPresent(existing -> {
                    throw new BookingRequestAlreadyExistsException(existing.id());
                });
        if (bookingOperatorId != null
                && users.findById(bookingOperatorId).filter(User::active).isEmpty()) {
            throw new BookingOperatorNotFoundException();
        }
        BookingRequest request = BookingRequest.createFromOrder(order, bookingOperatorId, notes, userId);
        bookingRequests.save(request);
        events.publishEvent(new BookingRequestCreated(
                request.id(),
                order.id(),
                order.proposalId(),
                order.opportunityId(),
                order.leadId(),
                userId,
                order.responsiblePersonId()));
        return request.id();
    }
}
