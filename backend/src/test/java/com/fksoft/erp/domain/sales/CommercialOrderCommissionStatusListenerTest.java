package com.fksoft.erp.domain.sales;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.commission.model.CommissionStatusChanged;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.service.CommercialOrderCommissionStatusListener;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the Sales-owned listener that reflects the Commission status summary onto the Order. */
@ExtendWith(MockitoExtension.class)
class CommercialOrderCommissionStatusListenerTest {

    @Mock
    private CommercialOrderRepository orders;

    @InjectMocks
    private CommercialOrderCommissionStatusListener listener;

    private CommercialOrder visibleOrder(UUID orderId) {
        CommercialOrder order = mock(CommercialOrder.class);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void reflectsAnActiveStatusOntoTheSourceOrderAsIs() {
        UUID orderId = UUID.randomUUID();
        CommercialOrder order = visibleOrder(orderId);

        listener.on(new CommissionStatusChanged(UUID.randomUUID(), orderId, "PAID"));

        verify(order).reflectCommissionStatus("PAID");
        verify(orders).save(order);
    }

    @Test
    void reflectsARejectedCommissionAsAnIssue() {
        UUID orderId = UUID.randomUUID();
        CommercialOrder order = visibleOrder(orderId);

        listener.on(new CommissionStatusChanged(UUID.randomUUID(), orderId, "REJECTED"));

        verify(order).reflectCommissionStatus("ISSUE");
    }

    @Test
    void reflectsACancelledCommissionAsAnIssue() {
        UUID orderId = UUID.randomUUID();
        CommercialOrder order = visibleOrder(orderId);

        listener.on(new CommissionStatusChanged(UUID.randomUUID(), orderId, "CANCELLED"));

        verify(order).reflectCommissionStatus("ISSUE");
    }

    @Test
    void ignoresAnUnknownOrderWithoutThrowing() {
        UUID orderId = UUID.randomUUID();
        when(orders.findById(orderId)).thenReturn(Optional.empty());

        listener.on(new CommissionStatusChanged(UUID.randomUUID(), orderId, "EXPECTED"));

        verify(orders, never()).save(any());
    }
}
