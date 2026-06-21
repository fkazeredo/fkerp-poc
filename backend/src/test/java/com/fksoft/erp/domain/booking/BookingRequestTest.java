package com.fksoft.erp.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.booking.exception.CommercialOrderNotPendingBookingException;
import com.fksoft.erp.domain.booking.model.BookingItem;
import com.fksoft.erp.domain.booking.model.BookingItemStatus;
import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
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

        BookingRequest request = BookingRequest.createFromOrder(order, OPERATOR, "Reservar com urgência", CREATOR);

        assertThat(request.status()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(request.commercialOrderId()).isEqualTo(order.id());
        assertThat(request.proposalId()).isEqualTo(order.proposalId());
        assertThat(request.opportunityId()).isEqualTo(OPP_ID);
        assertThat(request.leadId()).isEqualTo(LEAD_ID);
        assertThat(request.responsiblePersonId()).isEqualTo(RESPONSIBLE);
        assertThat(request.bookingOperatorId()).isEqualTo(OPERATOR);
        assertThat(request.notes()).isEqualTo("Reservar com urgência");
        assertThat(request.createdBy()).isEqualTo(CREATOR);

        // All order items become booking items, classified by booking need.
        assertThat(request.items()).hasSize(2);
        BookingItem pkg = itemOfType(request, ProposalItemType.TRAVEL_PACKAGE);
        assertThat(pkg.requiresBooking()).isTrue();
        assertThat(pkg.status()).isEqualTo(BookingItemStatus.PENDING);
        BookingItem fee = itemOfType(request, ProposalItemType.SERVICE_FEE);
        assertThat(fee.requiresBooking()).isFalse();
        assertThat(fee.status()).isEqualTo(BookingItemStatus.NOT_REQUIRED);
    }

    @Test
    void acceptsNullOperatorAndNotes() {
        BookingRequest request =
                BookingRequest.createFromOrder(pendingBookingOrder(ProposalItemType.CAR_RENTAL), null, null, CREATOR);

        assertThat(request.bookingOperatorId()).isNull();
        assertThat(request.notes()).isNull();
        assertThat(request.items().get(0).status()).isEqualTo(BookingItemStatus.PENDING);
    }

    @Test
    void rejectsCreatingFromAnOrderThatIsNotPendingBooking() {
        CommercialOrder notRequired =
                pendingBookingOrderRaw(ProposalItemType.SERVICE_FEE, ProposalItemType.OTHER); // BOOKING_NOT_REQUIRED
        assertThat(notRequired.status()).isEqualTo(CommercialOrderStatus.BOOKING_NOT_REQUIRED);

        assertThatThrownBy(() -> BookingRequest.createFromOrder(notRequired, null, null, CREATOR))
                .isInstanceOf(CommercialOrderNotPendingBookingException.class);
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
        when(o.stage()).thenReturn(OpportunityStage.READY_FOR_PROPOSAL);
        when(o.id()).thenReturn(OPP_ID);
        when(o.leadId()).thenReturn(LEAD_ID);
        CreateProposalCommand command = new CreateProposalCommand(
                OPP_ID, RESPONSIBLE, "Proposta corporativa", null, LocalDate.parse("2026-12-31"), "termos");
        return Proposal.createFromOpportunity(o, RESPONSIBLE, command, CREATOR);
    }
}
