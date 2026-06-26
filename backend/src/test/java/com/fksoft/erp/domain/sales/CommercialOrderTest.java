package com.fksoft.erp.domain.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
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

    private CommercialOrder order(Proposal proposal, long number) {
        return CommercialOrder.createFromProposal(proposal, CREATOR, number);
    }

    @Test
    void createsFromAnAcceptedProposalSnapshottingItemsTotalAndReferences() {
        Proposal proposal = acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE);

        CommercialOrder order = order(proposal, 7L);

        assertThat(order.number()).isEqualTo(7L);
        assertThat(order.proposalId()).isEqualTo(proposal.id());
        assertThat(order.opportunityId()).isEqualTo(OPP_ID);
        assertThat(order.leadId()).isEqualTo(LEAD_ID);
        assertThat(order.responsiblePersonId()).isEqualTo(RESPONSIBLE);
        assertThat(order.subtotal()).isEqualByComparingTo(proposal.subtotal());
        assertThat(order.total()).isEqualByComparingTo(proposal.total());
        assertThat(order.items()).hasSize(proposal.items().size());
        assertThat(order.items().get(0).type()).isEqualTo(ProposalItemTypeFixtures.TRAVEL_PACKAGE);
        assertThat(order.items().get(0).lineTotal())
                .isEqualByComparingTo(proposal.items().get(0).lineTotal());
        assertThat(order.createdBy()).isEqualTo(CREATOR);
        assertThat(order.isActive()).isTrue();
    }

    @Test
    void startsPendingBookingWhenItContainsABookableItem() {
        assertThat(order(acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE), 1L)
                        .status())
                .isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
        assertThat(order(acceptedProposalWith(ProposalItemTypeFixtures.CAR_RENTAL), 2L)
                        .status())
                .isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
        // A mix that includes a bookable item still requires booking.
        assertThat(order(
                                acceptedProposalWith(
                                        ProposalItemTypeFixtures.SERVICE_FEE, ProposalItemTypeFixtures.CAR_RENTAL),
                                3L)
                        .status())
                .isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
    }

    @Test
    void startsBookingNotRequiredWhenNoItemRequiresBooking() {
        CommercialOrder order =
                order(acceptedProposalWith(ProposalItemTypeFixtures.SERVICE_FEE, ProposalItemTypeFixtures.OTHER), 1L);

        assertThat(order.status()).isEqualTo(CommercialOrderStatus.BOOKING_NOT_REQUIRED);
    }

    @Test
    void rejectsCreatingFromANonAcceptedProposal() {
        Proposal accepted = acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE);
        // Build a Proposal that is only SENT (not accepted).
        Proposal onlySent = sentProposal();

        assertThatThrownBy(() -> order(onlySent, 1L)).isInstanceOf(ProposalNotAcceptedException.class);
        // Sanity: the accepted one does create an order.
        assertThat(order(accepted, 2L)).isNotNull();
    }

    @Test
    void startsWithNoReflectedBookingStatus() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE), 1L);
        assertThat(order.bookingStatus()).isNull();
    }

    @Test
    void reflectsTheConsolidatedBookingStatusWithoutChangingTheLifecycle() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE), 1L);

        order.reflectBookingStatus("CONFIRMED");

        // Identifiable as ready for Financial Operations, without touching the Order's own lifecycle (Sales-owned,
        // not cancelled — Booking takes no ownership).
        assertThat(order.bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(order.status()).isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
        assertThat(order.isActive()).isTrue();
    }

    @Test
    void aFailedBookingReflectionDoesNotCancelTheOrder() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE), 1L);

        order.reflectBookingStatus("FAILED");

        assertThat(order.bookingStatus()).isEqualTo("FAILED");
        assertThat(order.status()).isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
        assertThat(order.status()).isNotEqualTo(CommercialOrderStatus.CANCELLED);
    }

    @Test
    void startsWithNoReflectedFinancialStatus() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE), 1L);
        assertThat(order.financialStatus()).isNull();
    }

    @Test
    void reflectsTheFinancialStatusWithoutChangingTheLifecycle() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE), 1L);

        order.reflectFinancialStatus("PAID");

        // Identifiable as ready for Commission Management, without touching the Order's own lifecycle (Sales-owned).
        assertThat(order.financialStatus()).isEqualTo("PAID");
        assertThat(order.status()).isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
        assertThat(order.isActive()).isTrue();
    }

    @Test
    void anOverdueFinancialReflectionDoesNotCancelTheOrder() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE), 1L);

        order.reflectFinancialStatus("OVERDUE");

        assertThat(order.financialStatus()).isEqualTo("OVERDUE");
        assertThat(order.status()).isNotEqualTo(CommercialOrderStatus.CANCELLED);
    }

    @Test
    void startsWithNoReflectedCommissionStatus() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE), 1L);
        assertThat(order.commissionStatus()).isNull();
    }

    @Test
    void reflectsTheCommissionStatusWithoutChangingTheLifecycle() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE), 1L);

        order.reflectCommissionStatus("PAID");

        // Identifiable as having a paid commission (cycle closed), without touching the Order's own lifecycle.
        assertThat(order.commissionStatus()).isEqualTo("PAID");
        assertThat(order.status()).isEqualTo(CommercialOrderStatus.PENDING_BOOKING);
        assertThat(order.isActive()).isTrue();
    }

    @Test
    void aCommissionIssueReflectionDoesNotCancelTheOrder() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE), 1L);

        order.reflectCommissionStatus("ISSUE");

        assertThat(order.commissionStatus()).isEqualTo("ISSUE");
        assertThat(order.status()).isNotEqualTo(CommercialOrderStatus.CANCELLED);
    }

    @Test
    void reflectingANewStatusReplacesThePreviousReflection() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE), 1L);

        order.reflectBookingStatus("PENDING");
        order.reflectBookingStatus("PARTIALLY_CONFIRMED");

        assertThat(order.bookingStatus()).isEqualTo("PARTIALLY_CONFIRMED");
    }

    private Proposal acceptedProposalWith(ProposalItemType... types) {
        Proposal p = sentProposalWith(types);
        p.applyAccept(UUID.randomUUID(), "ok");
        return p;
    }

    private Proposal sentProposal() {
        return sentProposalWith(ProposalItemTypeFixtures.TRAVEL_PACKAGE);
    }

    private Proposal sentProposalWith(ProposalItemType... types) {
        Proposal p = readyDraft();
        for (ProposalItemType type : types) {
            p.addItem(
                    type,
                    new ProposalItemCommand(type.id(), "linha", 1, new BigDecimal("100.00"), null, null),
                    CREATOR);
        }
        p.applySubmit(CREATOR);
        p.applyApprove(UUID.randomUUID());
        p.applySend(UUID.randomUUID(), null);
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
