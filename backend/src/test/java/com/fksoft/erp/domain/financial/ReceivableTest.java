package com.fksoft.erp.domain.financial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.financial.exception.InstallmentNotPayableException;
import com.fksoft.erp.domain.financial.exception.InstallmentScheduleInvalidException;
import com.fksoft.erp.domain.financial.exception.OrderBookingNotConfirmedException;
import com.fksoft.erp.domain.financial.exception.PaymentAmountMismatchException;
import com.fksoft.erp.domain.financial.exception.PaymentInstallmentNotFoundException;
import com.fksoft.erp.domain.financial.model.InstallmentStatus;
import com.fksoft.erp.domain.financial.model.PaymentMethod;
import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableInstallment;
import com.fksoft.erp.domain.financial.model.ReceivablePayment;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.service.data.InstallmentInput;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the Receivable aggregate: creation from a confirmed Commercial Order, its guards and the
 * installment schedule. */
class ReceivableTest {

    private static final LocalDate DUE = LocalDate.of(2026, 7, 15);

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

    private CommercialOrder confirmedOrder(BigDecimal total) {
        return confirmedOrder(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), total);
    }

    private Customer customer() {
        Customer customer = mock(Customer.class);
        lenient().when(customer.id()).thenReturn(UUID.randomUUID());
        return customer;
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

        Receivable receivable = Receivable.createFromOrder(
                order, customer, DUE, "pay by boleto", financialResponsibleId, List.of(), by);

        assertThat(receivable.id()).isNotNull();
        assertThat(receivable.commercialOrderId()).isEqualTo(orderId);
        assertThat(receivable.proposalId()).isEqualTo(proposalId);
        assertThat(receivable.opportunityId()).isEqualTo(opportunityId);
        assertThat(receivable.leadId()).isEqualTo(leadId);
        assertThat(receivable.customerId()).isEqualTo(customerId);
        assertThat(receivable.commercialResponsiblePersonId()).isEqualTo(responsibleId);
        assertThat(receivable.financialResponsiblePersonId()).isEqualTo(financialResponsibleId);
        assertThat(receivable.totalAmount()).isEqualByComparingTo("1500.00");
        assertThat(receivable.dueDate()).isEqualTo(DUE);
        assertThat(receivable.paymentNotes()).isEqualTo("pay by boleto");
        assertThat(receivable.status()).isEqualTo(ReceivableStatus.OPEN);
        assertThat(receivable.createdBy()).isEqualTo(by);
        assertThat(receivable.updatedBy()).isEqualTo(by);
    }

    @Test
    void createFromOrderKeepsBlankNotesNull() {
        Receivable receivable = Receivable.createFromOrder(
                confirmedOrder(new BigDecimal("10.00")), customer(), DUE, "   ", null, null, UUID.randomUUID());

        assertThat(receivable.paymentNotes()).isNull();
        assertThat(receivable.financialResponsiblePersonId()).isNull();
    }

    @Test
    void createFromOrderWithoutAScheduleCreatesOneFullInstallment() {
        Receivable receivable = Receivable.createFromOrder(
                confirmedOrder(new BigDecimal("1500.00")), customer(), DUE, null, null, null, UUID.randomUUID());

        assertThat(receivable.installments()).hasSize(1);
        ReceivableInstallment only = receivable.installments().get(0);
        assertThat(only.number()).isEqualTo(1);
        assertThat(only.amount()).isEqualByComparingTo("1500.00");
        assertThat(only.dueDate()).isEqualTo(DUE);
        assertThat(only.status()).isEqualTo(InstallmentStatus.OPEN);
    }

    @Test
    void createFromOrderWithAMatchingScheduleCreatesNumberedOpenInstallments() {
        List<InstallmentInput> schedule = List.of(
                new InstallmentInput(new BigDecimal("500.00"), LocalDate.of(2026, 7, 1), "1/3"),
                new InstallmentInput(new BigDecimal("500.00"), LocalDate.of(2026, 8, 1), null),
                new InstallmentInput(new BigDecimal("500.00"), LocalDate.of(2026, 9, 1), null));

        Receivable receivable = Receivable.createFromOrder(
                confirmedOrder(new BigDecimal("1500.00")), customer(), DUE, null, null, schedule, UUID.randomUUID());

        assertThat(receivable.installments()).hasSize(3);
        assertThat(receivable.installments())
                .extracting(ReceivableInstallment::number)
                .containsExactly(1, 2, 3);
        assertThat(receivable.installments())
                .allSatisfy(i -> assertThat(i.status()).isEqualTo(InstallmentStatus.OPEN));
        assertThat(receivable.installments().get(0).amount()).isEqualByComparingTo("500.00");
        assertThat(receivable.installments().get(0).paymentNotes()).isEqualTo("1/3");
        assertThat(receivable.installments().get(2).dueDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void createFromOrderRejectsAScheduleThatDoesNotSumToTheTotal() {
        List<InstallmentInput> schedule = List.of(
                new InstallmentInput(new BigDecimal("500.00"), LocalDate.of(2026, 7, 1), null),
                new InstallmentInput(new BigDecimal("500.00"), LocalDate.of(2026, 8, 1), null));

        assertThatThrownBy(() -> Receivable.createFromOrder(
                        confirmedOrder(new BigDecimal("1500.00")),
                        customer(),
                        DUE,
                        null,
                        null,
                        schedule,
                        UUID.randomUUID()))
                .isInstanceOf(InstallmentScheduleInvalidException.class);
    }

    @Test
    void createFromOrderRejectsAScheduleWithANegativeInstallmentAmount() {
        List<InstallmentInput> schedule = List.of(
                new InstallmentInput(new BigDecimal("2000.00"), LocalDate.of(2026, 7, 1), null),
                new InstallmentInput(new BigDecimal("-500.00"), LocalDate.of(2026, 8, 1), null));

        assertThatThrownBy(() -> Receivable.createFromOrder(
                        confirmedOrder(new BigDecimal("1500.00")),
                        customer(),
                        DUE,
                        null,
                        null,
                        schedule,
                        UUID.randomUUID()))
                .isInstanceOf(InstallmentScheduleInvalidException.class);
    }

    @Test
    void createFromOrderRejectsAnOrderWhoseBookingIsNotConfirmed() {
        CommercialOrder order = mock(CommercialOrder.class);
        when(order.bookingStatus()).thenReturn("PARTIALLY_CONFIRMED");

        assertThatThrownBy(() ->
                        Receivable.createFromOrder(order, customer(), DUE, null, null, List.of(), UUID.randomUUID()))
                .isInstanceOf(OrderBookingNotConfirmedException.class);
    }

    @Test
    void createFromOrderRejectsAnOrderWithNoBookingYet() {
        CommercialOrder order = mock(CommercialOrder.class);
        when(order.bookingStatus()).thenReturn(null);

        assertThatThrownBy(() ->
                        Receivable.createFromOrder(order, customer(), DUE, null, null, List.of(), UUID.randomUUID()))
                .isInstanceOf(OrderBookingNotConfirmedException.class);
    }

    private PaymentMethod paymentMethod() {
        PaymentMethod method = mock(PaymentMethod.class);
        lenient().when(method.id()).thenReturn(UUID.randomUUID());
        return method;
    }

    private Receivable receivableWithSchedule(BigDecimal total, List<InstallmentInput> schedule) {
        return Receivable.createFromOrder(
                confirmedOrder(total), customer(), DUE, null, null, schedule, UUID.randomUUID());
    }

    @Test
    void registerFullPaymentOnTheOnlyInstallmentSettlesTheReceivable() {
        Receivable receivable = receivableWithSchedule(new BigDecimal("1500.00"), List.of());
        UUID installmentId = receivable.installments().get(0).id();
        UUID payer = UUID.randomUUID();
        LocalDate paidOn = LocalDate.of(2026, 6, 20);

        ReceivablePayment payment = receivable.registerFullPayment(
                installmentId, new BigDecimal("1500.00"), paidOn, paymentMethod(), "pix recebido", payer);

        assertThat(payment.id()).isNotNull();
        assertThat(payment.amount()).isEqualByComparingTo("1500.00");
        assertThat(payment.note()).isEqualTo("pix recebido");
        assertThat(receivable.installments().get(0).status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(receivable.status()).isEqualTo(ReceivableStatus.PAID);
        assertThat(receivable.amountPaid()).isEqualByComparingTo("1500.00");
        assertThat(receivable.lastPaymentDate()).isEqualTo(paidOn);
        assertThat(receivable.payments()).hasSize(1);
        assertThat(receivable.updatedBy()).isEqualTo(payer);
    }

    @Test
    void registerFullPaymentOnOneOfManyInstallmentsMarksPartiallyPaidThenPaid() {
        List<InstallmentInput> schedule = List.of(
                new InstallmentInput(new BigDecimal("500.00"), LocalDate.of(2026, 7, 1), null),
                new InstallmentInput(new BigDecimal("500.00"), LocalDate.of(2026, 8, 1), null),
                new InstallmentInput(new BigDecimal("500.00"), LocalDate.of(2026, 9, 1), null));
        Receivable receivable = receivableWithSchedule(new BigDecimal("1500.00"), schedule);

        receivable.registerFullPayment(
                receivable.installments().get(0).id(),
                new BigDecimal("500.00"),
                LocalDate.of(2026, 7, 2),
                paymentMethod(),
                null,
                UUID.randomUUID());

        assertThat(receivable.status()).isEqualTo(ReceivableStatus.PARTIALLY_PAID);
        assertThat(receivable.amountPaid()).isEqualByComparingTo("500.00");

        receivable.registerFullPayment(
                receivable.installments().get(1).id(),
                new BigDecimal("500.00"),
                LocalDate.of(2026, 8, 2),
                paymentMethod(),
                null,
                UUID.randomUUID());
        receivable.registerFullPayment(
                receivable.installments().get(2).id(),
                new BigDecimal("500.00"),
                LocalDate.of(2026, 9, 2),
                paymentMethod(),
                null,
                UUID.randomUUID());

        assertThat(receivable.status()).isEqualTo(ReceivableStatus.PAID);
        assertThat(receivable.amountPaid()).isEqualByComparingTo("1500.00");
        assertThat(receivable.lastPaymentDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(receivable.installments())
                .allSatisfy(i -> assertThat(i.status()).isEqualTo(InstallmentStatus.PAID));
        assertThat(receivable.payments()).hasSize(3);
    }

    @Test
    void registerFullPaymentRejectsAnUnknownInstallment() {
        Receivable receivable = receivableWithSchedule(new BigDecimal("100.00"), List.of());

        assertThatThrownBy(() -> receivable.registerFullPayment(
                        UUID.randomUUID(),
                        new BigDecimal("100.00"),
                        LocalDate.of(2026, 6, 20),
                        paymentMethod(),
                        null,
                        UUID.randomUUID()))
                .isInstanceOf(PaymentInstallmentNotFoundException.class);
    }

    @Test
    void registerFullPaymentRejectsAnAlreadyPaidInstallment() {
        Receivable receivable = receivableWithSchedule(new BigDecimal("100.00"), List.of());
        UUID installmentId = receivable.installments().get(0).id();
        receivable.registerFullPayment(
                installmentId,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 6, 20),
                paymentMethod(),
                null,
                UUID.randomUUID());

        assertThatThrownBy(() -> receivable.registerFullPayment(
                        installmentId,
                        new BigDecimal("100.00"),
                        LocalDate.of(2026, 6, 21),
                        paymentMethod(),
                        null,
                        UUID.randomUUID()))
                .isInstanceOf(InstallmentNotPayableException.class);
    }

    @Test
    void registerFullPaymentRejectsAnAmountThatDiffersFromTheInstallment() {
        Receivable receivable = receivableWithSchedule(new BigDecimal("100.00"), List.of());
        UUID installmentId = receivable.installments().get(0).id();

        assertThatThrownBy(() -> receivable.registerFullPayment(
                        installmentId,
                        new BigDecimal("60.00"),
                        LocalDate.of(2026, 6, 20),
                        paymentMethod(),
                        null,
                        UUID.randomUUID()))
                .isInstanceOf(PaymentAmountMismatchException.class);
        assertThat(receivable.installments().get(0).status()).isEqualTo(InstallmentStatus.OPEN);
        assertThat(receivable.payments()).isEmpty();
        assertThat(receivable.amountPaid()).isEqualByComparingTo("0.00");
    }

    @Test
    void registerFullPaymentKeepsTheLatestPaymentDateForOutOfOrderPayments() {
        List<InstallmentInput> schedule = List.of(
                new InstallmentInput(new BigDecimal("200.00"), LocalDate.of(2026, 7, 1), null),
                new InstallmentInput(new BigDecimal("300.00"), LocalDate.of(2026, 8, 1), null));
        Receivable receivable = receivableWithSchedule(new BigDecimal("500.00"), schedule);

        // Pay the first installment with a LATER date, then the second with an EARLIER one: the denormalized
        // last payment date must keep the latest (the earlier, out-of-order payment must not move it back).
        receivable.registerFullPayment(
                receivable.installments().get(0).id(),
                new BigDecimal("200.00"),
                LocalDate.of(2026, 6, 20),
                paymentMethod(),
                null,
                UUID.randomUUID());
        receivable.registerFullPayment(
                receivable.installments().get(1).id(),
                new BigDecimal("300.00"),
                LocalDate.of(2026, 6, 10),
                paymentMethod(),
                null,
                UUID.randomUUID());

        assertThat(receivable.lastPaymentDate()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(receivable.amountPaid()).isEqualByComparingTo("500.00");
        assertThat(receivable.status()).isEqualTo(ReceivableStatus.PAID);
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
