package com.fksoft.erp.domain.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.sales.exception.ProposalNotAcceptedException;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import com.fksoft.erp.domain.workflow.WorkflowDefinition;
import com.fksoft.erp.domain.workflow.WorkflowState;
import com.fksoft.erp.domain.workflow.WorkflowStateCategory;
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

    private final WorkflowDefinition wf = WorkflowDefinition.of("proposal", "Proposta");
    private final WorkflowState draft = WorkflowState.of(wf, "DRAFT", "Rascunho", WorkflowStateCategory.INITIAL, 1);
    private final WorkflowState readyForReview =
            WorkflowState.of(wf, "READY_FOR_REVIEW", "Em revisão", WorkflowStateCategory.ACTIVE, 2);
    private final WorkflowState approved =
            WorkflowState.of(wf, "APPROVED", "Aprovada", WorkflowStateCategory.ACTIVE, 3);
    private final WorkflowState sent = WorkflowState.of(wf, "SENT", "Enviada", WorkflowStateCategory.ACTIVE, 4);
    private final WorkflowState accepted =
            WorkflowState.of(wf, "ACCEPTED", "Aceita", WorkflowStateCategory.TERMINAL_POSITIVE, 5);

    private final WorkflowDefinition orderWf = WorkflowDefinition.of("order", "Pedido Comercial");
    private final WorkflowState pendingBooking =
            WorkflowState.of(orderWf, "PENDING_BOOKING", "Aguardando reserva", WorkflowStateCategory.INITIAL, 1);
    private final WorkflowState bookingNotRequired = WorkflowState.of(
            orderWf, "BOOKING_NOT_REQUIRED", "Sem reserva necessária", WorkflowStateCategory.INITIAL, 2);

    private CommercialOrder order(Proposal proposal, long number) {
        return CommercialOrder.createFromProposal(proposal, CREATOR, number, pendingBooking, bookingNotRequired);
    }

    @Test
    void createsFromAnAcceptedProposalSnapshottingItemsTotalAndReferences() {
        Proposal proposal = acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE);

        CommercialOrder order = order(proposal, 7L);

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
        assertThat(order(acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE), 1L)
                        .status())
                .isEqualTo("PENDING_BOOKING");
        assertThat(order(acceptedProposalWith(ProposalItemType.CAR_RENTAL), 2L).status())
                .isEqualTo("PENDING_BOOKING");
        // A mix that includes a bookable item still requires booking.
        assertThat(order(acceptedProposalWith(ProposalItemType.SERVICE_FEE, ProposalItemType.CAR_RENTAL), 3L)
                        .status())
                .isEqualTo("PENDING_BOOKING");
    }

    @Test
    void startsBookingNotRequiredWhenNoItemRequiresBooking() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemType.SERVICE_FEE, ProposalItemType.OTHER), 1L);

        assertThat(order.status()).isEqualTo("BOOKING_NOT_REQUIRED");
    }

    @Test
    void rejectsCreatingFromANonAcceptedProposal() {
        Proposal sent = acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE);
        // Build a Proposal that is only SENT (not accepted).
        Proposal onlySent = sentProposal();

        assertThatThrownBy(() -> order(onlySent, 1L)).isInstanceOf(ProposalNotAcceptedException.class);
        // Sanity: the accepted one does create an order.
        assertThat(order(sent, 2L)).isNotNull();
    }

    @Test
    void startsWithNoReflectedBookingStatus() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE), 1L);
        assertThat(order.bookingStatus()).isNull();
    }

    @Test
    void reflectsTheConsolidatedBookingStatusWithoutChangingTheLifecycle() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE), 1L);

        order.reflectBookingStatus("CONFIRMED");

        // Identifiable as ready for Financial Operations, without touching the Order's own lifecycle (Sales-owned,
        // not cancelled — Booking takes no ownership).
        assertThat(order.bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(order.status()).isEqualTo("PENDING_BOOKING");
        assertThat(order.isActive()).isTrue();
    }

    @Test
    void aFailedBookingReflectionDoesNotCancelTheOrder() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE), 1L);

        order.reflectBookingStatus("FAILED");

        assertThat(order.bookingStatus()).isEqualTo("FAILED");
        assertThat(order.status()).isEqualTo("PENDING_BOOKING");
        assertThat(order.status()).isNotEqualTo("CANCELLED");
    }

    @Test
    void reflectingANewStatusReplacesThePreviousReflection() {
        CommercialOrder order = order(acceptedProposalWith(ProposalItemType.TRAVEL_PACKAGE), 1L);

        order.reflectBookingStatus("PENDING");
        order.reflectBookingStatus("PARTIALLY_CONFIRMED");

        assertThat(order.bookingStatus()).isEqualTo("PARTIALLY_CONFIRMED");
    }

    private Proposal acceptedProposalWith(ProposalItemType... types) {
        Proposal p = sentProposalWith(types);
        p.applyAccept(accepted, UUID.randomUUID(), "ok");
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
        p.applySubmit(readyForReview, CREATOR);
        p.applyApprove(approved, UUID.randomUUID());
        p.applySend(sent, UUID.randomUUID(), null);
        return p;
    }

    private Proposal readyDraft() {
        Opportunity o = mock(Opportunity.class);
        when(o.stage()).thenReturn("READY_FOR_PROPOSAL");
        when(o.id()).thenReturn(OPP_ID);
        when(o.leadId()).thenReturn(LEAD_ID);
        CreateProposalCommand command = new CreateProposalCommand(
                OPP_ID, RESPONSIBLE, "Proposta corporativa", null, LocalDate.parse("2026-12-31"), "termos");
        return Proposal.createFromOpportunity(o, RESPONSIBLE, command, draft, CREATOR);
    }
}
