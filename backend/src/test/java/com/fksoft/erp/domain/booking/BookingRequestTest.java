package com.fksoft.erp.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.booking.exception.BookingItemNotMarkableException;
import com.fksoft.erp.domain.booking.exception.CommercialOrderNotPendingBookingException;
import com.fksoft.erp.domain.booking.model.BookingAttemptResult;
import com.fksoft.erp.domain.booking.model.BookingAttemptType;
import com.fksoft.erp.domain.booking.model.BookingFailureReason;
import com.fksoft.erp.domain.booking.model.BookingItem;
import com.fksoft.erp.domain.booking.model.BookingItemConfirmation;
import com.fksoft.erp.domain.booking.model.BookingItemFailure;
import com.fksoft.erp.domain.booking.model.BookingItemStatus;
import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domain invariants of the Booking Request aggregate (created from a PENDING_BOOKING Commercial Order). */
class BookingRequestTest {

    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final UUID OPERATOR = UUID.randomUUID();
    private static final UUID OPP_ID = UUID.randomUUID();
    private static final UUID LEAD_ID = UUID.randomUUID();

    @Test
    void createsFromPendingBookingOrderPreservingRefsAndClassifyingItems() {
        CommercialOrder order = pendingBookingOrder(ProposalItemType.TRAVEL_PACKAGE, ProposalItemType.SERVICE_FEE);

        BookingRequest request =
                BookingRequest.createFromOrder(order, OPERATOR, "Reservar com urgência", Set.of(), CREATOR);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(request.commercialOrderId()).isEqualTo(order.id());
        assertThat(request.proposalId()).isEqualTo(order.proposalId());
        assertThat(request.opportunityId()).isEqualTo(OPP_ID);
        assertThat(request.leadId()).isEqualTo(LEAD_ID);
        assertThat(request.responsiblePersonId()).isEqualTo(RESPONSIBLE);
        assertThat(request.bookingOperatorId()).isEqualTo(OPERATOR);
        assertThat(request.notes()).isEqualTo("Reservar com urgência");
        assertThat(request.createdBy()).isEqualTo(CREATOR);

        // Each order item becomes a booking item, classified by booking need, preserving the source line.
        assertThat(request.items()).hasSize(2);
        BookingItem pkg = itemOfType(request, ProposalItemType.TRAVEL_PACKAGE);
        assertThat(pkg.requiresBooking()).isTrue();
        assertThat(pkg.status()).isEqualTo(BookingItemStatus.PENDING);
        assertThat(pkg.orderItemId()).isEqualTo(orderItemId(order, ProposalItemType.TRAVEL_PACKAGE));
        assertThat(pkg.description()).isEqualTo("linha");
        BookingItem fee = itemOfType(request, ProposalItemType.SERVICE_FEE);
        assertThat(fee.requiresBooking()).isFalse();
        assertThat(fee.status()).isEqualTo(BookingItemStatus.NOT_REQUIRED);
    }

    @Test
    void otherItemRequiresBookingOnlyWhenExplicitlyMarked() {
        CommercialOrder order = pendingBookingOrder(ProposalItemType.TRAVEL_PACKAGE, ProposalItemType.OTHER);
        UUID otherId = orderItemId(order, ProposalItemType.OTHER);

        // Not marked → NOT_REQUIRED.
        BookingRequest unmarked = BookingRequest.createFromOrder(order, null, null, Set.of(), CREATOR);
        assertThat(itemOfType(unmarked, ProposalItemType.OTHER).requiresBooking())
                .isFalse();
        assertThat(itemOfType(unmarked, ProposalItemType.OTHER).status()).isEqualTo(BookingItemStatus.NOT_REQUIRED);

        // Explicitly marked → PENDING; the travel package still requires booking regardless.
        BookingRequest marked = BookingRequest.createFromOrder(order, null, null, Set.of(otherId), CREATOR);
        assertThat(itemOfType(marked, ProposalItemType.OTHER).requiresBooking()).isTrue();
        assertThat(itemOfType(marked, ProposalItemType.OTHER).status()).isEqualTo(BookingItemStatus.PENDING);
        assertThat(itemOfType(marked, ProposalItemType.TRAVEL_PACKAGE).requiresBooking())
                .isTrue();
    }

    @Test
    void rejectsMarkingANonOtherItem() {
        CommercialOrder order = pendingBookingOrder(ProposalItemType.TRAVEL_PACKAGE, ProposalItemType.SERVICE_FEE);
        UUID serviceFeeId = orderItemId(order, ProposalItemType.SERVICE_FEE);

        assertThatThrownBy(() -> BookingRequest.createFromOrder(order, null, null, Set.of(serviceFeeId), CREATOR))
                .isInstanceOf(BookingItemNotMarkableException.class);
    }

    @Test
    void rejectsMarkingAnIdNotInTheOrder() {
        CommercialOrder order = pendingBookingOrder(ProposalItemType.TRAVEL_PACKAGE);

        assertThatThrownBy(() -> BookingRequest.createFromOrder(order, null, null, Set.of(UUID.randomUUID()), CREATOR))
                .isInstanceOf(BookingItemNotMarkableException.class);
    }

    @Test
    void acceptsNullOperatorAndNotes() {
        BookingRequest request = BookingRequest.createFromOrder(
                pendingBookingOrder(ProposalItemType.CAR_RENTAL), null, null, Set.of(), CREATOR);

        assertThat(request.bookingOperatorId()).isNull();
        assertThat(request.notes()).isNull();
        assertThat(request.items().get(0).status()).isEqualTo(BookingItemStatus.PENDING);
    }

    @Test
    void rejectsCreatingFromAnOrderThatIsNotPendingBooking() {
        CommercialOrder notRequired =
                pendingBookingOrderRaw(ProposalItemType.SERVICE_FEE, ProposalItemType.OTHER); // BOOKING_NOT_REQUIRED
        assertThat(notRequired.status()).isEqualTo(CommercialOrderStatus.BOOKING_NOT_REQUIRED);

        assertThatThrownBy(() -> BookingRequest.createFromOrder(notRequired, null, null, Set.of(), CREATOR))
                .isInstanceOf(CommercialOrderNotPendingBookingException.class);
    }

    @Test
    void aFreshRequestWithNoAttemptStaysPending() {
        BookingRequest request = BookingRequest.createFromOrder(
                pendingBookingOrder(ProposalItemType.TRAVEL_PACKAGE), null, null, Set.of(), CREATOR);
        assertThat(request.status()).isEqualTo(BookingRequestStatus.PENDING);
    }

    @Test
    void recordingAnAttemptConsolidatesToInProgress() {
        BookingRequest request = BookingRequest.createFromOrder(
                pendingBookingOrder(ProposalItemType.TRAVEL_PACKAGE), null, null, Set.of(), CREATOR);

        request.recordAttempt(
                null,
                BookingAttemptType.INTERNAL_VERIFICATION,
                BookingAttemptResult.STARTED,
                "Checando disponibilidade",
                Instant.parse("2026-06-10T10:00:00Z"),
                null,
                CREATOR);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.IN_PROGRESS);
    }

    @Test
    void confirmingEveryRequiringItemConsolidatesToConfirmed() {
        BookingRequest request = BookingRequest.createFromOrder(
                pendingBookingOrder(ProposalItemType.TRAVEL_PACKAGE), null, null, Set.of(), CREATOR);

        request.confirmTravelPackageItem(
                itemOfType(request, ProposalItemType.TRAVEL_PACKAGE).id(), confirmation(), CREATOR);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.CONFIRMED);
    }

    @Test
    void confirmingSomeButNotAllRequiringItemsConsolidatesToPartiallyConfirmed() {
        BookingRequest request = BookingRequest.createFromOrder(
                pendingBookingOrder(ProposalItemType.TRAVEL_PACKAGE, ProposalItemType.CAR_RENTAL),
                null,
                null,
                Set.of(),
                CREATOR);

        request.confirmTravelPackageItem(
                itemOfType(request, ProposalItemType.TRAVEL_PACKAGE).id(), confirmation(), CREATOR);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.PARTIALLY_CONFIRMED);
    }

    @Test
    void failingWithNothingConfirmedConsolidatesToFailed() {
        BookingRequest request = BookingRequest.createFromOrder(
                pendingBookingOrder(ProposalItemType.TRAVEL_PACKAGE), null, null, Set.of(), CREATOR);

        request.failBookingItem(
                itemOfType(request, ProposalItemType.TRAVEL_PACKAGE).id(), failure(), CREATOR);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.FAILED);
    }

    @Test
    void confirmingAFailedItemReconsolidatesTheRequest() {
        BookingRequest request = BookingRequest.createFromOrder(
                pendingBookingOrder(ProposalItemType.TRAVEL_PACKAGE), null, null, Set.of(), CREATOR);
        UUID itemId = itemOfType(request, ProposalItemType.TRAVEL_PACKAGE).id();
        request.failBookingItem(itemId, failure(), CREATOR);
        assertThat(request.status()).isEqualTo(BookingRequestStatus.FAILED);

        request.confirmTravelPackageItem(itemId, confirmation(), CREATOR);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.CONFIRMED);
    }

    private static BookingItemConfirmation confirmation() {
        return BookingItemConfirmation.builder()
                .externalSystem("Amadeus")
                .externalLocator("ABC123")
                .confirmedAt(Instant.parse("2026-06-10T10:00:00Z"))
                .confirmedBy(CREATOR)
                .build();
    }

    private static BookingItemFailure failure() {
        return BookingItemFailure.builder()
                .failureReason(BookingFailureReason.NO_AVAILABILITY)
                .failedBy(CREATOR)
                .failedAt(Instant.parse("2026-06-10T10:00:00Z"))
                .build();
    }

    @Test
    void bookingItemCarriesNoMonetaryData() {
        // A Booking Request is not financial data — the booking item must not snapshot any money field.
        for (Field f : BookingItem.class.getDeclaredFields()) {
            String name = f.getName().toLowerCase();
            assertThat(name)
                    .as("BookingItem must carry no monetary field, found: " + f.getName())
                    .doesNotContain("value")
                    .doesNotContain("price")
                    .doesNotContain("total")
                    .doesNotContain("discount")
                    .doesNotContain("subtotal");
        }
    }

    private static BookingItem itemOfType(BookingRequest request, ProposalItemType type) {
        return request.items().stream()
                .filter(i -> i.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private static UUID orderItemId(CommercialOrder order, ProposalItemType type) {
        return order.items().stream()
                .filter(i -> i.type() == type)
                .findFirst()
                .orElseThrow()
                .id();
    }

    private CommercialOrder pendingBookingOrder(ProposalItemType... types) {
        return pendingBookingOrderRaw(types);
    }

    private CommercialOrder pendingBookingOrderRaw(ProposalItemType... types) {
        return CommercialOrder.createFromProposal(acceptedProposalWith(types), CREATOR, 1L);
    }

    private Proposal acceptedProposalWith(ProposalItemType... types) {
        Proposal p = readyDraft();
        for (ProposalItemType type : types) {
            p.addItem(new ProposalItemCommand(type, "linha", 1, new BigDecimal("100.00"), null, null), CREATOR);
        }
        p.submitForReview(CREATOR);
        p.approve(UUID.randomUUID());
        p.markAsSent(UUID.randomUUID(), null);
        p.acceptByCustomer(UUID.randomUUID(), "ok");
        return p;
    }

    private Proposal readyDraft() {
        Opportunity o = mock(Opportunity.class);
        when(o.stage()).thenReturn("READY_FOR_PROPOSAL");
        when(o.id()).thenReturn(OPP_ID);
        when(o.leadId()).thenReturn(LEAD_ID);
        CreateProposalCommand command = new CreateProposalCommand(
                OPP_ID, RESPONSIBLE, "Proposta corporativa", null, LocalDate.parse("2026-12-31"), "termos");
        return Proposal.createFromOpportunity(o, RESPONSIBLE, command, CREATOR);
    }
}
