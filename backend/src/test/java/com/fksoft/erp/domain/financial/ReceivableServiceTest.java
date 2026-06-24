package com.fksoft.erp.domain.financial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.crm.repository.CustomerRepository;
import com.fksoft.erp.domain.crm.service.CustomerService;
import com.fksoft.erp.domain.financial.exception.InstallmentScheduleInvalidException;
import com.fksoft.erp.domain.financial.exception.OrderBookingNotConfirmedException;
import com.fksoft.erp.domain.financial.exception.PaymentExceedsOutstandingException;
import com.fksoft.erp.domain.financial.exception.PaymentMethodNotAvailableException;
import com.fksoft.erp.domain.financial.exception.ReceivableAccessDeniedException;
import com.fksoft.erp.domain.financial.exception.ReceivableAlreadyExistsException;
import com.fksoft.erp.domain.financial.exception.ReceivableNotFoundException;
import com.fksoft.erp.domain.financial.exception.SourceOrderAccessDeniedException;
import com.fksoft.erp.domain.financial.exception.SourceOrderNotFoundException;
import com.fksoft.erp.domain.financial.model.PaymentMethod;
import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableCreated;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.model.ReceivableStatusChanged;
import com.fksoft.erp.domain.financial.repository.PaymentMethodRepository;
import com.fksoft.erp.domain.financial.repository.ReceivableRepository;
import com.fksoft.erp.domain.financial.service.ReceivableAccessPolicy;
import com.fksoft.erp.domain.financial.service.ReceivableService;
import com.fksoft.erp.domain.financial.service.data.CreateReceivableCommand;
import com.fksoft.erp.domain.financial.service.data.InstallmentInput;
import com.fksoft.erp.domain.financial.service.data.ReceivableDetail;
import com.fksoft.erp.domain.financial.service.data.RegisterPaymentCommand;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.service.OrderAccessPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests of the Receivable Application Service (create + detail) with all collaborators mocked. */
@ExtendWith(MockitoExtension.class)
class ReceivableServiceTest {

    @Mock
    private ReceivableRepository receivables;

    @Mock
    private PaymentMethodRepository paymentMethods;

    @Mock
    private ReceivableAccessPolicy accessPolicy;

    @Mock
    private CommercialOrderRepository orders;

    @Mock
    private OrderAccessPolicy orderAccessPolicy;

    @Mock
    private CustomerService customerService;

    @Mock
    private CustomerRepository customers;

    @Mock
    private com.fksoft.erp.domain.sales.repository.ProposalRepository proposals;

    @Mock
    private com.fksoft.erp.domain.crm.repository.OpportunityRepository opportunities;

    @Mock
    private UserRepository users;

    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    @InjectMocks
    private ReceivableService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID leadId = UUID.randomUUID();

    private CommercialOrder confirmedOrder() {
        CommercialOrder order = mock(CommercialOrder.class);
        lenient().when(order.id()).thenReturn(orderId);
        lenient().when(order.bookingStatus()).thenReturn("CONFIRMED");
        lenient().when(order.proposalId()).thenReturn(UUID.randomUUID());
        lenient().when(order.opportunityId()).thenReturn(UUID.randomUUID());
        lenient().when(order.leadId()).thenReturn(leadId);
        lenient().when(order.responsiblePersonId()).thenReturn(UUID.randomUUID());
        lenient().when(order.total()).thenReturn(new BigDecimal("1500.00"));
        return order;
    }

    private CreateReceivableCommand command() {
        return new CreateReceivableCommand(orderId, LocalDate.of(2026, 7, 15), "boleto", null, List.of());
    }

    private CreateReceivableCommand command(List<InstallmentInput> installments) {
        return new CreateReceivableCommand(orderId, LocalDate.of(2026, 7, 15), "boleto", null, installments);
    }

    @Test
    void createBuildsAnOpenReceivableAndPublishesTheEvents() {
        CommercialOrder order = confirmedOrder();
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(order, userId, true, false)).thenReturn(true);
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(orderId, ReceivableStatus.active()))
                .thenReturn(Optional.empty());
        Customer customer = mock(Customer.class);
        UUID customerId = UUID.randomUUID();
        when(customer.id()).thenReturn(customerId);
        when(customerService.findOrCreateFromLead(leadId, userId)).thenReturn(customer);
        when(receivables.save(any(Receivable.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID result = service.create(command(), userId, true, false);

        ArgumentCaptor<Receivable> saved = ArgumentCaptor.forClass(Receivable.class);
        verify(receivables).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ReceivableStatus.OPEN);
        assertThat(saved.getValue().customerId()).isEqualTo(customerId);
        assertThat(saved.getValue().totalAmount()).isEqualByComparingTo("1500.00");
        // No explicit schedule → one full-amount installment.
        assertThat(saved.getValue().installments()).hasSize(1);
        assertThat(saved.getValue().installments().get(0).amount()).isEqualByComparingTo("1500.00");
        assertThat(result).isEqualTo(saved.getValue().id());

        // Two facts are published: ReceivableCreated (for Commission Management) and ReceivableStatusChanged
        // (OPEN — reflected onto the Commercial Order).
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(published.capture());
        ReceivableCreated created = published.getAllValues().stream()
                .filter(ReceivableCreated.class::isInstance)
                .map(ReceivableCreated.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(created.commercialOrderId()).isEqualTo(orderId);
        assertThat(created.customerId()).isEqualTo(customerId);
        ReceivableStatusChanged reflected = published.getAllValues().stream()
                .filter(ReceivableStatusChanged.class::isInstance)
                .map(ReceivableStatusChanged.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(reflected.status()).isEqualTo("OPEN");
        assertThat(reflected.commercialOrderId()).isEqualTo(orderId);
    }

    @Test
    void createPassesAMatchingMultiInstallmentScheduleThrough() {
        CommercialOrder order = confirmedOrder();
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(order, userId, true, false)).thenReturn(true);
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(orderId, ReceivableStatus.active()))
                .thenReturn(Optional.empty());
        Customer customer = mock(Customer.class);
        when(customer.id()).thenReturn(UUID.randomUUID());
        when(customerService.findOrCreateFromLead(leadId, userId)).thenReturn(customer);
        when(receivables.save(any(Receivable.class))).thenAnswer(inv -> inv.getArgument(0));
        List<InstallmentInput> schedule = List.of(
                new InstallmentInput(new BigDecimal("1000.00"), LocalDate.of(2026, 7, 1), null),
                new InstallmentInput(new BigDecimal("500.00"), LocalDate.of(2026, 8, 1), null));

        service.create(command(schedule), userId, true, false);

        ArgumentCaptor<Receivable> saved = ArgumentCaptor.forClass(Receivable.class);
        verify(receivables).save(saved.capture());
        assertThat(saved.getValue().installments()).hasSize(2);
        assertThat(saved.getValue().installments().get(0).amount()).isEqualByComparingTo("1000.00");
        assertThat(saved.getValue().installments().get(1).number()).isEqualTo(2);
    }

    @Test
    void createRejectsAScheduleThatDoesNotSumToTheTotal() {
        CommercialOrder order = confirmedOrder();
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(order, userId, true, false)).thenReturn(true);
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(orderId, ReceivableStatus.active()))
                .thenReturn(Optional.empty());
        when(customerService.findOrCreateFromLead(leadId, userId)).thenReturn(mock(Customer.class));
        List<InstallmentInput> schedule =
                List.of(new InstallmentInput(new BigDecimal("100.00"), LocalDate.of(2026, 7, 1), null));

        assertThatThrownBy(() -> service.create(command(schedule), userId, true, false))
                .isInstanceOf(InstallmentScheduleInvalidException.class);
        verify(receivables, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void createThrowsWhenTheSourceOrderDoesNotExist() {
        when(orders.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(), userId, true, false))
                .isInstanceOf(SourceOrderNotFoundException.class);
        verify(receivables, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void createThrowsWhenTheCallerCannotSeeTheSourceOrder() {
        CommercialOrder order = confirmedOrder();
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(eq(order), eq(userId), anyBoolean(), anyBoolean()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.create(command(), userId, false, false))
                .isInstanceOf(SourceOrderAccessDeniedException.class);
        verify(receivables, never()).save(any());
    }

    @Test
    void createThrowsWhenTheOrderBookingIsNotConfirmed() {
        CommercialOrder order = mock(CommercialOrder.class);
        when(order.bookingStatus()).thenReturn("FAILED");
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(order, userId, true, false)).thenReturn(true);

        assertThatThrownBy(() -> service.create(command(), userId, true, false))
                .isInstanceOf(OrderBookingNotConfirmedException.class);
        verify(receivables, never()).save(any());
    }

    @Test
    void createThrowsWhenTheOrderAlreadyHasAnActiveReceivable() {
        CommercialOrder order = confirmedOrder();
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(order, userId, true, false)).thenReturn(true);
        Receivable existing = mock(Receivable.class);
        UUID existingId = UUID.randomUUID();
        when(existing.id()).thenReturn(existingId);
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(orderId, ReceivableStatus.active()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(command(), userId, true, false))
                .isInstanceOf(ReceivableAlreadyExistsException.class)
                .satisfies(ex -> assertThat(((ReceivableAlreadyExistsException) ex).details())
                        .containsEntry("receivableId", existingId.toString()));
        verify(receivables, never()).save(any());
        verify(customerService, never()).findOrCreateFromLead(any(), any());
    }

    @Test
    void detailThrowsNotFoundWhenAbsent() {
        UUID id = UUID.randomUUID();
        when(receivables.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(id, userId, true)).isInstanceOf(ReceivableNotFoundException.class);
    }

    @Test
    void detailThrowsAccessDeniedWhenNotVisible() {
        UUID id = UUID.randomUUID();
        Receivable receivable = mock(Receivable.class);
        when(receivables.findById(id)).thenReturn(Optional.of(receivable));
        when(accessPolicy.canSee(receivable, userId, false)).thenReturn(false);

        assertThatThrownBy(() -> service.detail(id, userId, false)).isInstanceOf(ReceivableAccessDeniedException.class);
    }

    private Customer mockCustomer() {
        Customer customer = mock(Customer.class);
        lenient().when(customer.id()).thenReturn(UUID.randomUUID());
        return customer;
    }

    private Receivable openReceivable() {
        return Receivable.createFromOrder(
                confirmedOrder(), mockCustomer(), LocalDate.of(2026, 7, 15), null, null, List.of(), userId);
    }

    @Test
    void registerPaymentSettlesTheInstallmentAndReturnsTheDetail() {
        Receivable receivable = openReceivable();
        UUID installmentId = receivable.installments().get(0).id();
        when(receivables.findById(receivable.id())).thenReturn(Optional.of(receivable));
        when(accessPolicy.canSee(receivable, userId, true)).thenReturn(true);
        UUID methodId = UUID.randomUUID();
        PaymentMethod method = mock(PaymentMethod.class);
        lenient().when(method.id()).thenReturn(methodId);
        lenient().when(method.code()).thenReturn("PIX");
        lenient().when(method.label()).thenReturn("Pix");
        when(method.active()).thenReturn(true);
        when(paymentMethods.findById(methodId)).thenReturn(Optional.of(method));
        when(receivables.save(any(Receivable.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterPaymentCommand cmd =
                new RegisterPaymentCommand(methodId, new BigDecimal("1500.00"), LocalDate.of(2026, 6, 20), "pix");
        ReceivableDetail detail = service.registerPayment(receivable.id(), installmentId, cmd, userId, true);

        assertThat(detail.status()).isEqualTo("PAID");
        assertThat(detail.amountPaid()).isEqualByComparingTo("1500.00");
        assertThat(detail.outstandingAmount()).isEqualByComparingTo("0.00");
        assertThat(detail.payments()).hasSize(1);
        assertThat(detail.payments().get(0).paymentMethodLabel()).isEqualTo("Pix");
        assertThat(detail.payments().get(0).installmentNumber()).isEqualTo(1);
        verify(receivables).save(receivable);
        // The consolidated status (PAID) is reflected onto the Commercial Order.
        ArgumentCaptor<ReceivableStatusChanged> reflected = ArgumentCaptor.forClass(ReceivableStatusChanged.class);
        verify(events).publishEvent(reflected.capture());
        assertThat(reflected.getValue().status()).isEqualTo("PAID");
        assertThat(reflected.getValue().commercialOrderId()).isEqualTo(receivable.commercialOrderId());
    }

    @Test
    void registerPaymentThrowsNotFoundWhenReceivableAbsent() {
        UUID id = UUID.randomUUID();
        when(receivables.findById(id)).thenReturn(Optional.empty());

        RegisterPaymentCommand cmd =
                new RegisterPaymentCommand(UUID.randomUUID(), new BigDecimal("10.00"), LocalDate.of(2026, 6, 20), null);
        assertThatThrownBy(() -> service.registerPayment(id, UUID.randomUUID(), cmd, userId, true))
                .isInstanceOf(ReceivableNotFoundException.class);
        verify(receivables, never()).save(any());
    }

    @Test
    void registerPaymentThrowsAccessDeniedWhenNotVisible() {
        UUID id = UUID.randomUUID();
        Receivable receivable = mock(Receivable.class);
        when(receivables.findById(id)).thenReturn(Optional.of(receivable));
        when(accessPolicy.canSee(receivable, userId, false)).thenReturn(false);

        RegisterPaymentCommand cmd =
                new RegisterPaymentCommand(UUID.randomUUID(), new BigDecimal("10.00"), LocalDate.of(2026, 6, 20), null);
        assertThatThrownBy(() -> service.registerPayment(id, UUID.randomUUID(), cmd, userId, false))
                .isInstanceOf(ReceivableAccessDeniedException.class);
        verify(paymentMethods, never()).findById(any());
    }

    @Test
    void registerPaymentThrowsWhenPaymentMethodUnknownOrInactive() {
        Receivable receivable = openReceivable();
        when(receivables.findById(receivable.id())).thenReturn(Optional.of(receivable));
        when(accessPolicy.canSee(receivable, userId, true)).thenReturn(true);
        UUID methodId = UUID.randomUUID();
        when(paymentMethods.findById(methodId)).thenReturn(Optional.empty());

        RegisterPaymentCommand cmd =
                new RegisterPaymentCommand(methodId, new BigDecimal("1500.00"), LocalDate.of(2026, 6, 20), null);
        assertThatThrownBy(() -> service.registerPayment(
                        receivable.id(), receivable.installments().get(0).id(), cmd, userId, true))
                .isInstanceOf(PaymentMethodNotAvailableException.class);
        verify(receivables, never()).save(any());
    }

    @Test
    void registerPaymentRecordsAPartialPaymentLeavingThePartiallyPaidDetail() {
        Receivable receivable = openReceivable();
        UUID installmentId = receivable.installments().get(0).id();
        when(receivables.findById(receivable.id())).thenReturn(Optional.of(receivable));
        when(accessPolicy.canSee(receivable, userId, true)).thenReturn(true);
        UUID methodId = UUID.randomUUID();
        PaymentMethod method = mock(PaymentMethod.class);
        lenient().when(method.id()).thenReturn(methodId);
        lenient().when(method.code()).thenReturn("PIX");
        lenient().when(method.label()).thenReturn("Pix");
        when(method.active()).thenReturn(true);
        when(paymentMethods.findById(methodId)).thenReturn(Optional.of(method));
        when(receivables.save(any(Receivable.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterPaymentCommand cmd =
                new RegisterPaymentCommand(methodId, new BigDecimal("600.00"), LocalDate.of(2026, 6, 20), null);
        ReceivableDetail detail = service.registerPayment(receivable.id(), installmentId, cmd, userId, true);

        assertThat(detail.status()).isEqualTo("PARTIALLY_PAID");
        assertThat(detail.amountPaid()).isEqualByComparingTo("600.00");
        assertThat(detail.outstandingAmount()).isEqualByComparingTo("900.00");
        assertThat(detail.installments().get(0).status()).isEqualTo("PARTIALLY_PAID");
        assertThat(detail.installments().get(0).amountPaid()).isEqualByComparingTo("600.00");
        assertThat(detail.installments().get(0).outstanding()).isEqualByComparingTo("900.00");
        assertThat(detail.payments()).hasSize(1);
    }

    @Test
    void registerPaymentPropagatesTheExceedsOutstandingError() {
        Receivable receivable = openReceivable();
        when(receivables.findById(receivable.id())).thenReturn(Optional.of(receivable));
        when(accessPolicy.canSee(receivable, userId, true)).thenReturn(true);
        UUID methodId = UUID.randomUUID();
        PaymentMethod method = mock(PaymentMethod.class);
        when(method.active()).thenReturn(true);
        when(paymentMethods.findById(methodId)).thenReturn(Optional.of(method));

        RegisterPaymentCommand cmd =
                new RegisterPaymentCommand(methodId, new BigDecimal("2000.00"), LocalDate.of(2026, 6, 20), null);
        assertThatThrownBy(() -> service.registerPayment(
                        receivable.id(), receivable.installments().get(0).id(), cmd, userId, true))
                .isInstanceOf(PaymentExceedsOutstandingException.class);
        verify(receivables, never()).save(any());
    }
}
