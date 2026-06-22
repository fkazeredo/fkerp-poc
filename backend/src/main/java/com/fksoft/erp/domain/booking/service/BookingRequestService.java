package com.fksoft.erp.domain.booking.service;

import com.fksoft.erp.domain.booking.exception.BookingOperatorNotFoundException;
import com.fksoft.erp.domain.booking.exception.BookingRequestAccessDeniedException;
import com.fksoft.erp.domain.booking.exception.BookingRequestAlreadyExistsException;
import com.fksoft.erp.domain.booking.exception.BookingRequestNotFoundException;
import com.fksoft.erp.domain.booking.model.BookingAttempt;
import com.fksoft.erp.domain.booking.model.BookingItem;
import com.fksoft.erp.domain.booking.model.BookingItemConfirmation;
import com.fksoft.erp.domain.booking.model.BookingItemFailure;
import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestCreated;
import com.fksoft.erp.domain.booking.model.BookingRequestPendingReasons;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.booking.model.BookingStatusConsolidated;
import com.fksoft.erp.domain.booking.repository.BookingIndicatorQueries;
import com.fksoft.erp.domain.booking.repository.BookingItemCountsRow;
import com.fksoft.erp.domain.booking.repository.BookingPendingItemCountsRow;
import com.fksoft.erp.domain.booking.repository.BookingRequestRepository;
import com.fksoft.erp.domain.booking.service.data.BookingIndicators;
import com.fksoft.erp.domain.booking.service.data.BookingRequestDetail;
import com.fksoft.erp.domain.booking.service.data.BookingRequestListItem;
import com.fksoft.erp.domain.booking.service.data.BookingRequestSearchCriteria;
import com.fksoft.erp.domain.booking.service.data.ConfirmCarRentalCommand;
import com.fksoft.erp.domain.booking.service.data.ConfirmTravelPackageCommand;
import com.fksoft.erp.domain.booking.service.data.FailBookingItemCommand;
import com.fksoft.erp.domain.booking.service.data.PendingBookingRequest;
import com.fksoft.erp.domain.booking.service.data.RecordBookingAttemptCommand;
import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.exception.OpportunityNotFoundException;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.sales.exception.CommercialOrderAccessDeniedException;
import com.fksoft.erp.domain.sales.exception.CommercialOrderNotFoundException;
import com.fksoft.erp.domain.sales.exception.ProposalNotFoundException;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.domain.sales.service.OrderAccessPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    private final BookingIndicatorQueries indicatorQueries;
    private final CommercialOrderRepository orders;
    private final OrderAccessPolicy orderAccessPolicy;
    private final BookingRequestAccessPolicy accessPolicy;
    private final ProposalRepository proposals;
    private final OpportunityRepository opportunities;
    private final LeadRepository leads;
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
     * @param bookingRequiredItemIds ids of the Order's OTHER items to mark as requiring booking, or
     *     {@code null} (each must be an OTHER item of the Order)
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
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemNotMarkableException if a marked id is not an
     *     OTHER item of the Order
     */
    @Transactional
    public UUID create(
            UUID commercialOrderId,
            UUID bookingOperatorId,
            String notes,
            Set<UUID> bookingRequiredItemIds,
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
        Set<UUID> required = bookingRequiredItemIds == null ? Collections.emptySet() : bookingRequiredItemIds;
        BookingRequest request = BookingRequest.createFromOrder(order, bookingOperatorId, notes, required, userId);
        bookingRequests.save(request);
        events.publishEvent(new BookingRequestCreated(
                request.id(),
                order.id(),
                order.proposalId(),
                order.opportunityId(),
                order.leadId(),
                userId,
                order.responsiblePersonId()));
        // Reflect the initial (PENDING) booking status onto the source Commercial Order (Sales owns the write).
        publishConsolidated(request);
        return request.id();
    }

    /**
     * Operational, paginated Booking Request list, filtered by the given criteria and restricted to the
     * caller's visibility. The terminal CONFIRMED and CANCELLED requests are excluded unless the status filter
     * explicitly includes them; FAILED stays visible by default. Enriches each item with the source Order's
     * number (the human identifier), the source Proposal's title (the commercial reference), the operator and
     * commercial-responsible names, and the item counts (requiring booking / confirmed). Exposes operational
     * reservation data only — never Financial, Payment or Commission data.
     *
     * @param criteria the optional filters
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every request
     * @param canSeeUnassigned whether the caller may also see the unassigned (no-operator) pool
     * @return a page of Booking Request list items
     */
    @Transactional(readOnly = true)
    public Page<BookingRequestListItem> list(
            BookingRequestSearchCriteria criteria,
            Pageable pageable,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        Specification<BookingRequest> spec = BookingRequestSpecifications.matching(criteria)
                .and(accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned));
        Page<BookingRequest> page = bookingRequests.findAll(spec, pageable);

        Map<UUID, String> names = resolveNames(
                page.getContent().stream().flatMap(r -> Stream.of(r.bookingOperatorId(), r.responsiblePersonId())));
        Map<UUID, Long> orderNumbers =
                resolveOrderNumbers(page.getContent().stream().map(BookingRequest::commercialOrderId));
        Map<UUID, String> proposalTitles =
                resolveProposalTitles(page.getContent().stream().map(BookingRequest::proposalId));
        Map<UUID, long[]> itemCounts = resolveItemCounts(
                page.getContent().stream().map(BookingRequest::id).toList());

        return page.map(r -> {
            long[] counts = itemCounts.getOrDefault(r.id(), EMPTY_COUNTS);
            return BookingRequestListItem.from(
                    r,
                    orderNumbers.getOrDefault(r.commercialOrderId(), 0L),
                    proposalTitles.get(r.proposalId()),
                    nameOf(names, r.bookingOperatorId()),
                    nameOf(names, r.responsiblePersonId()),
                    counts[0],
                    counts[1]);
        });
    }

    private static final long[] EMPTY_COUNTS = {0L, 0L};

    /**
     * Operational pending-items worklist of the Booking Requests visible to the caller that need action (no
     * booking operator, still pending, in progress with no recent attempt, a failed item, a requiring-booking
     * item still pending, partially confirmed, or an overdue next action), each tagged with its reasons. Read
     * only; creates no Financial, Payment, Commission or Customer Care data and never modifies a request. Terminal
     * CONFIRMED / CANCELLED requests are excluded.
     *
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every request
     * @param canSeeUnassigned whether the caller may also see the unassigned (no-operator) pool
     * @return a page of pending Booking Request items
     */
    @Transactional(readOnly = true)
    public Page<PendingBookingRequest> pending(
            Pageable pageable, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Specification<BookingRequest> spec = BookingRequestPendingSpecifications.pending(now, today)
                .and(accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned));
        Page<BookingRequest> page = bookingRequests.findAll(spec, pageable);

        Map<UUID, String> names = resolveNames(
                page.getContent().stream().flatMap(r -> Stream.of(r.bookingOperatorId(), r.responsiblePersonId())));
        Map<UUID, Long> orderNumbers =
                resolveOrderNumbers(page.getContent().stream().map(BookingRequest::commercialOrderId));
        Map<UUID, String> proposalTitles =
                resolveProposalTitles(page.getContent().stream().map(BookingRequest::proposalId));
        Map<UUID, long[]> counts = resolvePendingItemCounts(
                page.getContent().stream().map(BookingRequest::id).toList());

        return page.map(r -> {
            long[] c = counts.getOrDefault(r.id(), EMPTY_PENDING_COUNTS);
            boolean hasFailed = c[2] > 0;
            boolean hasPendingRequired = c[3] > 0;
            return PendingBookingRequest.from(
                    r,
                    orderNumbers.getOrDefault(r.commercialOrderId(), 0L),
                    proposalTitles.get(r.proposalId()),
                    nameOf(names, r.bookingOperatorId()),
                    nameOf(names, r.responsiblePersonId()),
                    c,
                    BookingRequestPendingReasons.of(r, now, today, hasFailed, hasPendingRequired));
        });
    }

    private static final long[] EMPTY_PENDING_COUNTS = {0L, 0L, 0L, 0L};

    /**
     * Minimum Booking Operations indicators over the requests visible to the caller. The volume figures (total,
     * by status, items by type, failed items, average creation→confirmation time) cover the requested period (by
     * creation date); the operational figure (ready for Financial Operations = currently CONFIRMED) is a current
     * snapshot of all the visible requests. Read-only; exposes operational reservation figures only — never
     * Financial, Payment, Commission, Customer Care or external-integration data.
     *
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every request
     * @param canSeeUnassigned whether the caller may also see the unassigned (no-operator) pool
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return the indicators
     */
    @Transactional(readOnly = true)
    public BookingIndicators indicators(
            UUID userId, boolean canSeeAll, boolean canSeeUnassigned, Instant from, Instant to) {
        Specification<BookingRequest> visible = accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned);

        // Volume — over the period.
        Map<BookingRequestStatus, Long> countByStatus = indicatorQueries.countByStatus(visible, from, to);
        long total = countByStatus.values().stream().mapToLong(Long::longValue).sum();
        List<BookingIndicators.StatusCount> byStatus = countByStatus.entrySet().stream()
                .map(e -> new BookingIndicators.StatusCount(e.getKey(), e.getValue()))
                .toList();
        List<BookingIndicators.ItemTypeCount> itemsByType =
                indicatorQueries.countItemsByType(visible, from, to).entrySet().stream()
                        .map(e -> new BookingIndicators.ItemTypeCount(e.getKey(), e.getValue()))
                        .toList();
        long failedItems = indicatorQueries.countFailedItems(visible, from, to);
        Long avgConfirmationSeconds = indicatorQueries.avgConfirmationSeconds(visible, from, to);

        // Operational — current snapshot (no period): how many are ready for Financial Operations now.
        long readyForFinance =
                indicatorQueries.countByStatus(visible, null, null).getOrDefault(BookingRequestStatus.CONFIRMED, 0L);

        return new BookingIndicators(
                total, byStatus, itemsByType, failedItems, readyForFinance, avgConfirmationSeconds);
    }

    /**
     * Full detail of a Booking Request the caller is allowed to see, with the source Commercial Order,
     * Proposal, Opportunity and Lead kept traceable and each booking item carrying its booking status (the
     * per-item confirmation/failure signal). Read-only; exposes operational reservation data only — never
     * Financial, Payment or Commission data.
     *
     * @param id the booking request id
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every request
     * @param canSeeUnassigned whether the caller may also see the unassigned (no-operator) pool
     * @return the detail read model
     * @throws BookingRequestNotFoundException if the request does not exist
     * @throws BookingRequestAccessDeniedException if the caller may not see it
     */
    @Transactional(readOnly = true)
    public BookingRequestDetail detail(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        return toDetail(loadVisible(id, userId, canSeeAll, canSeeUnassigned));
    }

    /**
     * Registers a manual booking attempt on a Booking Request the caller is allowed to see, and returns the
     * refreshed detail. The attempt is append-only operational history; registering it moves the request from
     * PENDING to IN_PROGRESS but never confirms the booking, never changes a booking item's status, and never
     * creates Financial or Commission data.
     *
     * @param id the booking request id
     * @param command the attempt data (optional item link, type, result, description, date, optional next action)
     * @param userId the acting user (the attempt's author)
     * @param canSeeAll whether the caller may see every request
     * @param canSeeUnassigned whether the caller may also see the unassigned (no-operator) pool
     * @return the updated detail read model
     * @throws BookingRequestNotFoundException if the request does not exist
     * @throws BookingRequestAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemNotFoundException if the linked item is not in
     *     the request
     */
    @Transactional
    public BookingRequestDetail recordAttempt(
            UUID id, RecordBookingAttemptCommand command, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        BookingRequest request = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        request.recordAttempt(
                command.bookingItemId(),
                command.type(),
                command.result(),
                command.description(),
                command.occurredAt(),
                command.nextActionDate(),
                userId);
        return saveAndReflect(request);
    }

    /**
     * Manually confirms a Travel Package booking item of a Booking Request the caller is allowed to see, and
     * returns the refreshed detail. Records the external reservation result on the item, moves it to CONFIRMED
     * and consolidates the request status (PARTIALLY_CONFIRMED / CONFIRMED). No external call is made and no
     * Financial, Payment, Commission or Customer Care data is created.
     *
     * @param id the booking request id
     * @param itemId the booking item to confirm
     * @param command the confirmation data (external system + locator + date + optional travel metadata)
     * @param userId the acting user (who confirmed)
     * @param canSeeAll whether the caller may see every request
     * @param canSeeUnassigned whether the caller may also see the unassigned (no-operator) pool
     * @return the updated detail read model
     * @throws BookingRequestNotFoundException if the request does not exist
     * @throws BookingRequestAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemNotFoundException if the item is not in the request
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemNotConfirmableException if the item is not a
     *     confirmable Travel Package item
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemAlreadyResolvedException if the item is already
     *     confirmed or cancelled
     */
    @Transactional
    public BookingRequestDetail confirmTravelPackageItem(
            UUID id,
            UUID itemId,
            ConfirmTravelPackageCommand command,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        BookingRequest request = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        BookingItemConfirmation confirmation = BookingItemConfirmation.builder()
                .externalSystem(command.externalSystem())
                .externalLocator(command.externalLocator())
                .confirmedAt(command.confirmedAt())
                .confirmedBy(userId)
                .packageDescription(command.packageDescription())
                .travelStartDate(command.travelStartDate())
                .travelEndDate(command.travelEndDate())
                .travelerNotes(command.travelerNotes())
                .operationalNotes(command.operationalNotes())
                .build();
        request.confirmTravelPackageItem(itemId, confirmation, userId);
        return saveAndReflect(request);
    }

    /**
     * Manually confirms a Car Rental booking item of a Booking Request the caller is allowed to see, and returns
     * the refreshed detail. Records the external reservation result on the item, moves it to CONFIRMED and
     * consolidates the request status (PARTIALLY_CONFIRMED / CONFIRMED). No external call is made and no
     * Financial, Payment, Commission or Customer Care data is created.
     *
     * @param id the booking request id
     * @param itemId the booking item to confirm
     * @param command the confirmation data (external system + locator + date + optional car-rental metadata)
     * @param userId the acting user (who confirmed)
     * @param canSeeAll whether the caller may see every request
     * @param canSeeUnassigned whether the caller may also see the unassigned (no-operator) pool
     * @return the updated detail read model
     * @throws BookingRequestNotFoundException if the request does not exist
     * @throws BookingRequestAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemNotFoundException if the item is not in the request
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemNotConfirmableException if the item is not a
     *     confirmable Car Rental item
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemAlreadyResolvedException if the item is already
     *     confirmed or cancelled
     */
    @Transactional
    public BookingRequestDetail confirmCarRentalItem(
            UUID id,
            UUID itemId,
            ConfirmCarRentalCommand command,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        BookingRequest request = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        BookingItemConfirmation confirmation = BookingItemConfirmation.builder()
                .externalSystem(command.externalSystem())
                .externalLocator(command.externalLocator())
                .confirmedAt(command.confirmedAt())
                .confirmedBy(userId)
                .rentalCompany(command.rentalCompany())
                .pickupLocation(command.pickupLocation())
                .dropoffLocation(command.dropoffLocation())
                .pickupAt(command.pickupAt())
                .dropoffAt(command.dropoffAt())
                .carCategory(command.carCategory())
                .operationalNotes(command.operationalNotes())
                .build();
        request.confirmCarRentalItem(itemId, confirmation, userId);
        return saveAndReflect(request);
    }

    /**
     * Marks a booking item of a Booking Request the caller is allowed to see as failed, and returns the
     * refreshed detail. Records the failure on the item, moves it to FAILED and consolidates the request status
     * (FAILED / PARTIALLY_CONFIRMED). The failed item stays visible and may later be retried/confirmed. The
     * Commercial Order is not cancelled and no Financial, Payment, Commission or Customer Care data is created.
     *
     * @param id the booking request id
     * @param itemId the booking item to fail
     * @param command the failure data (reason, optional note, date)
     * @param userId the acting user (who marked the failure)
     * @param canSeeAll whether the caller may see every request
     * @param canSeeUnassigned whether the caller may also see the unassigned (no-operator) pool
     * @return the updated detail read model
     * @throws BookingRequestNotFoundException if the request does not exist
     * @throws BookingRequestAccessDeniedException if the caller may not see it
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemNotFoundException if the item is not in the request
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemNotFailableException if the item does not require
     *     booking
     * @throws com.fksoft.erp.domain.booking.exception.BookingItemAlreadyResolvedException if the item is already
     *     confirmed or cancelled
     */
    @Transactional
    public BookingRequestDetail failBookingItem(
            UUID id,
            UUID itemId,
            FailBookingItemCommand command,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        BookingRequest request = loadVisible(id, userId, canSeeAll, canSeeUnassigned);
        BookingItemFailure failure = BookingItemFailure.builder()
                .failureReason(command.failureReason())
                .failureNote(command.failureNote())
                .failedBy(userId)
                .failedAt(command.failedAt())
                .build();
        request.failBookingItem(itemId, failure, userId);
        return saveAndReflect(request);
    }

    // Persists the request, reflects its consolidated status onto the source Commercial Order (Sales-owned,
    // via a synchronous domain-event reaction in the same transaction), and returns the refreshed detail.
    private BookingRequestDetail saveAndReflect(BookingRequest request) {
        BookingRequest saved = bookingRequests.saveAndFlush(request);
        publishConsolidated(saved);
        return toDetail(saved);
    }

    private void publishConsolidated(BookingRequest request) {
        events.publishEvent(new BookingStatusConsolidated(request.id(), request.commercialOrderId(), request.status()));
    }

    private BookingRequest loadVisible(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        BookingRequest request = bookingRequests.findById(id).orElseThrow(BookingRequestNotFoundException::new);
        if (!accessPolicy.canSee(request, userId, canSeeAll, canSeeUnassigned)) {
            throw new BookingRequestAccessDeniedException();
        }
        return request;
    }

    private BookingRequestDetail toDetail(BookingRequest request) {
        CommercialOrder order =
                orders.findById(request.commercialOrderId()).orElseThrow(CommercialOrderNotFoundException::new);
        Proposal proposal = proposals.findById(request.proposalId()).orElseThrow(ProposalNotFoundException::new);
        Opportunity opportunity =
                opportunities.findById(request.opportunityId()).orElseThrow(OpportunityNotFoundException::new);
        Lead lead = leads.findById(request.leadId()).orElseThrow(LeadNotFoundException::new);
        // Resolve display names for the operator/responsible/creator, the attempt authors, and the per-item
        // confirmation and failure authors (so the detail can show who confirmed/failed each item).
        Stream<UUID> base = Stream.of(request.bookingOperatorId(), request.responsiblePersonId(), request.createdBy());
        Stream<UUID> attemptAuthors = request.attempts().stream().map(BookingAttempt::registeredBy);
        Stream<UUID> confirmAuthors = request.items().stream()
                .map(BookingItem::confirmation)
                .filter(Objects::nonNull)
                .map(BookingItemConfirmation::confirmedBy);
        Stream<UUID> failAuthors = request.items().stream()
                .map(BookingItem::failure)
                .filter(Objects::nonNull)
                .map(BookingItemFailure::failedBy);
        Map<UUID, String> names = resolveNames(
                Stream.of(base, attemptAuthors, confirmAuthors, failAuthors).flatMap(s -> s));
        return BookingRequestDetail.from(request, order, proposal, opportunity, lead, names);
    }

    private Map<UUID, String> resolveNames(Stream<UUID> ids) {
        Set<UUID> set = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : users.findAllById(set).stream().collect(Collectors.toMap(User::id, User::username));
    }

    private Map<UUID, Long> resolveOrderNumbers(Stream<UUID> ids) {
        Set<UUID> set = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : orders.findAllById(set).stream()
                        .collect(Collectors.toMap(CommercialOrder::id, CommercialOrder::number));
    }

    private Map<UUID, String> resolveProposalTitles(Stream<UUID> ids) {
        Set<UUID> set = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : proposals.findAllById(set).stream().collect(Collectors.toMap(Proposal::id, Proposal::title));
    }

    private Map<UUID, long[]> resolveItemCounts(java.util.List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return bookingRequests.findItemCounts(ids).stream()
                .collect(Collectors.toMap(BookingItemCountsRow::getBookingRequestId, row ->
                        new long[] {row.getRequiring(), row.getConfirmed()}));
    }

    private Map<UUID, long[]> resolvePendingItemCounts(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return bookingRequests.findPendingItemCounts(ids).stream()
                .collect(
                        Collectors.toMap(BookingPendingItemCountsRow::getBookingRequestId, row -> new long[] {
                            row.getRequiring(), row.getConfirmed(), row.getFailed(), row.getPendingRequired()
                        }));
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }
}
