package com.fksoft.erp.domain.sales;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.financial.model.ReceivableStatusChanged;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.service.CommercialOrderFinancialStatusListener;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the Sales-owned listener that reflects the Receivable's financial status onto the Order. */
@ExtendWith(MockitoExtension.class)
class CommercialOrderFinancialStatusListenerTest {

    @Mock
    private CommercialOrderRepository orders;

    @InjectMocks
    private CommercialOrderFinancialStatusListener listener;

    @Test
    void reflectsTheFinancialStatusOntoTheSourceOrder() {
        UUID orderId = UUID.randomUUID();
        CommercialOrder order = mock(CommercialOrder.class);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));

        listener.on(new ReceivableStatusChanged(UUID.randomUUID(), orderId, "PAID"));

        verify(order).reflectFinancialStatus("PAID");
        verify(orders).save(order);
    }

    @Test
    void ignoresAnUnknownOrderWithoutThrowing() {
        UUID orderId = UUID.randomUUID();
        when(orders.findById(orderId)).thenReturn(Optional.empty());

        listener.on(new ReceivableStatusChanged(UUID.randomUUID(), orderId, "OPEN"));

        verify(orders, never()).save(any());
    }
}
