package com.fksoft.erp.domain.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.sales.exception.ProposalNotAcceptedException;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domain invariants of the Commercial Order aggregate (snapshot of an Accepted Proposal). */
class CommercialOrderTest {

    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final UUID OPP_ID = UUID.randomUUID();
    private static final UUID LEAD_ID = UUID.randomUUID();

    @Test
    void createsFromAnAcceptedProposalSnapshottingItemsTotalAndReferences() {
        Proposal proposal = acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE);

        CommercialOrder order = CommercialOrder.createFromProposal(proposal, CREATOR, 7L);

        assertThat(order.number()).isEqualTo(7L);
        assertThat(order.proposalId()).isEqualTo(proposal.id());
        assertThat(order.opportunityId()).isEqualTo(OPP_ID);
        assertThat(order.leadId()).isEqualTo(LEAD_ID);
        assertThat(order.responsiblePersonId()).isEqualTo(RESPONSIBLE);
        assertThat(order.subtotal()).isEqualByComparingTo(proposal.subtotal());
        assertThat(order.total()).isEqualByComparingTo(proposal.total());
        assertThat(order.items()).hasSize(proposal.items().size());
        assertThat(order.items().get(0).type()).isEqualTo(ProposalItemType.TRAVEL_PACKAGE);
        assertThat(order.items().get(0).lineTotal())
                .isEqualByComparingTo(proposal.items().get(0).lineTotal());
        assertThat(order.createdBy()).isEqualTo(CREATOR);
        assertThat(order.isActive()).isTrue();
    }

    @Test
    void startsPendingBookingWhenItContainsABookableItem() {
        assertThat(CommercialOrder.createFromProposal(
                                acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE), CREATOR, 1L)
                        .status())
                .isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
        assertThat(CommercialOrder.createFromProposal(acceptedProposalWith(ProposalItemType.CAR_RENTAL), CREATOR, 2L)
                        .status())
                .isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
        // A mix that includes a bookable item still requires booking.
        assertThat(CommercialOrder.createFromProposal(
                                acceptedProposalWith(ProposalItemType.SERVICE_FEE, ProposalItemType.CAR_RENTAL),
                                CREATOR,
                                3L)
                        .status())
                .isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
    }

    @Test
    void startsBookingNotRequiredWhenNoItemRequiresBooking() {
        CommercialOrder order = CommercialOrder.createFromProposal(
                acceptedProposalWith(ProposalItemType.SERVICE_FEE, ProposalItemType.OTHER), CREATOR, 1L);

        assertThat(order.status()).isEqualTo(CommercialOrderStatus.BOOKING_NOT_REQUIRED);
    }

    @Test
    void rejectsCreatingFromANonAcceptedProposal() {
        Proposal sent = acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE);
        // Build a Proposal that is only SENT (not accepted).
        Proposal onlySent = sentProposal();

        assertThatThrownBy(() -> CommercialOrder.createFromProposal(onlySent, CREATOR, 1L))
                .isInstanceOf(ProposalNotAcceptedException.class);
        // Sanity: the accepted one does create an order.
        assertThat(CommercialOrder.createFromProposal(sent, CREATOR, 2L)).isNotNull();
    }

    @Test
    void startsWithNoReflectedBookingStatus() {
        CommercialOrder order =
                CommercialOrder.createFromProposal(acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE), CREATOR, 1L);
        assertThat(order.bookingStatus()).isNull();
    }

    @Test
    void reflectsTheConsolidatedBookingStatusWithoutChangingTheLifecycle() {
        CommercialOrder order =
                CommercialOrder.createFromProposal(acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE), CREATOR, 1L);

        order.reflectBookingStatus(BookingRequestStatus.CONFIRMED);

        // Identifiable as ready for Financial Operations, without touching the Order's own lifecycle (Sales-owned,
        // not cancelled — Booking takes no ownership).
        assertThat(order.bookingStatus()).isEqualTo(BookingRequestStatus.CONFIRMED);
        assertThat(order.status()).isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
        assertThat(order.isActive()).isTrue();
    }

    @Test
    void aFailedBookingReflectionDoesNotCancelTheOrder() {
        CommercialOrder order =
                CommercialOrder.createFromProposal(acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE), CREATOR, 1L);

        order.reflectBookingStatus(BookingRequestStatus.FAILED);

        assertThat(order.bookingStatus()).isEqualTo(BookingRequestStatus.FAILED);
        assertThat(order.status()).isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
        assertThat(order.status()).isNotEqualTo(CommercialOrderStatus.CANCELLED);
    }

    @Test
    void reflectingANewStatusReplacesThePreviousReflection() {
        CommercialOrder order =
                CommercialOrder.createFromProposal(acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE), CREATOR, 1L);

        order.reflectBookingStatus(BookingRequestStatus.PENDING);
        order.reflectBookingStatus(BookingRequestStatus.PARTIALLY_CONFIRMED);

        assertThat(order.bookingStatus()).isEqualTo(BookingRequestStatus.PARTIALLY_CONFIRMED);
    }

    private Proposal acceptedProposalWith(ProposalItemType... types) {
        Proposal p = sentProposalWith(types);
        p.acceptByCustomer(UUID.randomUUID(), "ok");
        return p;
    }

    private Proposal sentProposal() {
        return sentProposalWith(ProposalItemType.TRAVEL_PACKAGE);
    }

    private Proposal sentProposalWith(ProposalItemType... types) {
        Proposal p = readyDraft();
        for (ProposalItemType type : types) {
            p.addItem(new ProposalItemCommand(type, "linha", 1, new BigDecimal("100.00"), null, null), CREATOR);
        }
        p.submitForReview(CREATOR);
        p.approve(UUID.randomUUID());
        p.markAsSent(UUID.randomUUID(), null);
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
