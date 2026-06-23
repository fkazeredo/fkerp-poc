package com.fksoft.erp.domain.financial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.financial.exception.OrderBookingNotConfirmedException;
import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the Receivable aggregate: creation from a confirmed Commercial Order and its guards. */
class ReceivableTest {

    private CommercialOrder confirmedOrder(
            UUID id, UUID proposalId, UUID opportunityId, UUID leadId, UUID responsibleId, BigDecimal total) {
        CommercialOrder order = mock(CommercialOrder.class);
        when(order.bookingStatus()).thenReturn("CONFIRMED");
        lenient().when(order.id()).thenReturn(id);
        lenient().when(order.proposalId()).thenReturn(proposalId);
        lenient().when(order.opportunityId()).thenReturn(opportunityId);
        lenient().when(order.leadId()).thenReturn(leadId);
        lenient().when(order.responsiblePersonId()).thenReturn(responsibleId);
        lenient().when(order.total()).thenReturn(total);
        return order;
    }

    @Test
    void createFromOrderSnapshotsTheCommercialOriginAndTotalAndStartsOpen() {
        UUID orderId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        UUID financialResponsibleId = UUID.randomUUID();
        UUID by = UUID.randomUUID();
        CommercialOrder order =
                confirmedOrder(orderId, proposalId, opportunityId, leadId, responsibleId, new BigDecimal("1500.00"));
        Customer customer = mock(Customer.class);
        UUID customerId = UUID.randomUUID();
        when(customer.id()).thenReturn(customerId);
        LocalDate dueDate = LocalDate.of(2026, 7, 15);

        Receivable receivable =
                Receivable.createFromOrder(order, customer, dueDate, "pay by boleto", financialResponsibleId, by);

        assertThat(receivable.id()).isNotNull();
        assertThat(receivable.commercialOrderId()).isEqualTo(orderId);
        assertThat(receivable.proposalId()).isEqualTo(proposalId);
        assertThat(receivable.opportunityId()).isEqualTo(opportunityId);
        assertThat(receivable.leadId()).isEqualTo(leadId);
        assertThat(receivable.customerId()).isEqualTo(customerId);
        assertThat(receivable.commercialResponsiblePersonId()).isEqualTo(responsibleId);
        assertThat(receivable.financialResponsiblePersonId()).isEqualTo(financialResponsibleId);
        assertThat(receivable.totalAmount()).isEqualByComparingTo("1500.00");
        assertThat(receivable.dueDate()).isEqualTo(dueDate);
        assertThat(receivable.paymentNotes()).isEqualTo("pay by boleto");
        assertThat(receivable.status()).isEqualTo(ReceivableStatus.OPEN);
        assertThat(receivable.createdBy()).isEqualTo(by);
        assertThat(receivable.updatedBy()).isEqualTo(by);
    }

    @Test
    void createFromOrderKeepsBlankNotesNull() {
        CommercialOrder order = confirmedOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10.00"));
        Customer customer = mock(Customer.class);
        when(customer.id()).thenReturn(UUID.randomUUID());

        Receivable receivable =
                Receivable.createFromOrder(order, customer, LocalDate.of(2026, 7, 15), "   ", null, UUID.randomUUID());

        assertThat(receivable.paymentNotes()).isNull();
        assertThat(receivable.financialResponsiblePersonId()).isNull();
    }

    @Test
    void createFromOrderRejectsAnOrderWhoseBookingIsNotConfirmed() {
        CommercialOrder order = mock(CommercialOrder.class);
        when(order.bookingStatus()).thenReturn("PARTIALLY_CONFIRMED");
        Customer customer = mock(Customer.class);

        assertThatThrownBy(() -> Receivable.createFromOrder(
                        order, customer, LocalDate.of(2026, 7, 15), null, null, UUID.randomUUID()))
                .isInstanceOf(OrderBookingNotConfirmedException.class);
    }

    @Test
    void createFromOrderRejectsAnOrderWithNoBookingYet() {
        CommercialOrder order = mock(CommercialOrder.class);
        when(order.bookingStatus()).thenReturn(null);
        Customer customer = mock(Customer.class);

        assertThatThrownBy(() -> Receivable.createFromOrder(
                        order, customer, LocalDate.of(2026, 7, 15), null, null, UUID.randomUUID()))
                .isInstanceOf(OrderBookingNotConfirmedException.class);
    }

    @Test
    void activeStatusesExcludeOnlyCancelled() {
        assertThat(ReceivableStatus.active())
                .containsExactlyInAnyOrder(
                        ReceivableStatus.OPEN,
                        ReceivableStatus.PARTIALLY_PAID,
                        ReceivableStatus.PAID,
                        ReceivableStatus.OVERDUE)
                .doesNotContain(ReceivableStatus.CANCELLED);
    }
}
