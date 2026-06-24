package com.fksoft.erp.domain.financial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.model.ReceivableStatusChanged;
import com.fksoft.erp.domain.financial.repository.ReceivableRepository;
import com.fksoft.erp.domain.financial.service.ReceivableOverdueJob;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Unit tests for the daily overdue check: it flags past-due operational Receivables and republishes the status. */
@ExtendWith(MockitoExtension.class)
class ReceivableOverdueJobTest {

    private static final LocalDate DUE = LocalDate.of(2026, 7, 15);
    private static final List<ReceivableStatus> OPERATIONAL =
            List.of(ReceivableStatus.OPEN, ReceivableStatus.PARTIALLY_PAID);

    @Mock
    private ReceivableRepository receivables;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private ReceivableOverdueJob job;

    private Receivable openReceivable() {
        CommercialOrder order = mock(CommercialOrder.class);
        when(order.bookingStatus()).thenReturn("CONFIRMED");
        lenient().when(order.id()).thenReturn(UUID.randomUUID());
        lenient().when(order.proposalId()).thenReturn(UUID.randomUUID());
        lenient().when(order.opportunityId()).thenReturn(UUID.randomUUID());
        lenient().when(order.leadId()).thenReturn(UUID.randomUUID());
        lenient().when(order.responsiblePersonId()).thenReturn(UUID.randomUUID());
        lenient().when(order.total()).thenReturn(new BigDecimal("500.00"));
        Customer customer = mock(Customer.class);
        lenient().when(customer.id()).thenReturn(UUID.randomUUID());
        return Receivable.createFromOrder(order, customer, DUE, null, null, List.of(), UUID.randomUUID());
    }

    @Test
    void flagsPastDueOperationalReceivablesAndPublishesTheReflection() {
        Receivable receivable = openReceivable();
        when(receivables.findByStatusIn(OPERATIONAL)).thenReturn(List.of(receivable));

        int count = job.markOverdue(DUE.plusDays(1));

        assertThat(count).isEqualTo(1);
        assertThat(receivable.status()).isEqualTo(ReceivableStatus.OVERDUE);
        verify(receivables).save(receivable);
        ArgumentCaptor<ReceivableStatusChanged> event = ArgumentCaptor.forClass(ReceivableStatusChanged.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().status()).isEqualTo("OVERDUE");
        assertThat(event.getValue().commercialOrderId()).isEqualTo(receivable.commercialOrderId());
        assertThat(event.getValue().receivableId()).isEqualTo(receivable.id());
    }

    @Test
    void leavesANotYetDueReceivableUntouched() {
        Receivable receivable = openReceivable();
        when(receivables.findByStatusIn(OPERATIONAL)).thenReturn(List.of(receivable));

        int count = job.markOverdue(DUE.minusDays(1));

        assertThat(count).isZero();
        assertThat(receivable.status()).isEqualTo(ReceivableStatus.OPEN);
        verify(receivables, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
