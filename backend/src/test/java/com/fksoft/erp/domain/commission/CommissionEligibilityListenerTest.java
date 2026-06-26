package com.fksoft.erp.domain.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.commission.model.Commission;
import com.fksoft.erp.domain.commission.model.CommissionBasis;
import com.fksoft.erp.domain.commission.model.CommissionRule;
import com.fksoft.erp.domain.commission.model.CommissionRuleData;
import com.fksoft.erp.domain.commission.model.CommissionStatus;
import com.fksoft.erp.domain.commission.model.CommissionStatusChanged;
import com.fksoft.erp.domain.commission.model.CommissionTargetType;
import com.fksoft.erp.domain.commission.repository.CommissionRepository;
import com.fksoft.erp.domain.commission.service.CommissionEligibilityListener;
import com.fksoft.erp.domain.financial.model.ReceivableStatusChanged;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests of the Commission eligibility listener: an Expected Commission becomes Eligible only when its
 * Receivable is reported PAID, and a non-PAID status (partial/open/overdue, including a reversal downgrade) is
 * ignored (no clawback). Making a commission eligible is a no-op when the Order has no commission.
 */
@ExtendWith(MockitoExtension.class)
class CommissionEligibilityListenerTest {

    @Mock
    private CommissionRepository commissions;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private CommissionEligibilityListener listener;

    private final UUID orderId = UUID.randomUUID();
    private final UUID receivableId = UUID.randomUUID();

    private Commission expectedCommission() {
        CommercialOrder order = mock(CommercialOrder.class);
        lenient().when(order.id()).thenReturn(orderId);
        lenient().when(order.proposalId()).thenReturn(UUID.randomUUID());
        lenient().when(order.opportunityId()).thenReturn(UUID.randomUUID());
        lenient().when(order.leadId()).thenReturn(UUID.randomUUID());
        lenient().when(order.responsiblePersonId()).thenReturn(UUID.randomUUID());
        CommissionRule rule = CommissionRule.create(
                new CommissionRuleData(
                        "R",
                        new BigDecimal("5"),
                        CommissionTargetType.COMMERCIAL_RESPONSIBLE,
                        null,
                        LocalDate.of(2026, 1, 1),
                        null,
                        null),
                UUID.randomUUID());
        return Commission.generate(
                order, rule, CommissionBasis.COMMERCIAL_AMOUNT, new BigDecimal("1000.00"), order.id());
    }

    @Test
    void makesTheExpectedCommissionEligibleWhenTheReceivableIsPaid() {
        Commission commission = expectedCommission();
        when(commissions.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.of(commission));

        listener.on(new ReceivableStatusChanged(receivableId, orderId, "PAID"));

        assertThat(commission.status()).isEqualTo(CommissionStatus.ELIGIBLE);
        assertThat(commission.eligibleAt()).isNotNull();
        assertThat(commission.receivableId()).isEqualTo(receivableId);
        verify(commissions).save(commission);

        // The eligibility transition publishes the status change so Sales can reflect ELIGIBLE onto the Order.
        ArgumentCaptor<CommissionStatusChanged> published = ArgumentCaptor.forClass(CommissionStatusChanged.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue().commercialOrderId()).isEqualTo(orderId);
        assertThat(published.getValue().status()).isEqualTo("ELIGIBLE");
    }

    @Test
    void doesNothingWhenThePaidOrderHasNoExpectedCommission() {
        when(commissions.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.empty());

        listener.on(new ReceivableStatusChanged(receivableId, orderId, "PAID"));

        verify(commissions, never()).save(any());
    }

    @Test
    void ignoresAPartiallyPaidReceivable() {
        listener.on(new ReceivableStatusChanged(receivableId, orderId, "PARTIALLY_PAID"));
        verifyNoInteractions(commissions);
    }

    @Test
    void ignoresAnOpenReceivable() {
        listener.on(new ReceivableStatusChanged(receivableId, orderId, "OPEN"));
        verifyNoInteractions(commissions);
    }

    @Test
    void ignoresAReversalDowngrade() {
        // A reversal republishes a non-PAID status; the listener must not regress an eligible commission (no clawback).
        listener.on(new ReceivableStatusChanged(receivableId, orderId, "OVERDUE"));
        verifyNoInteractions(commissions);
    }
}
