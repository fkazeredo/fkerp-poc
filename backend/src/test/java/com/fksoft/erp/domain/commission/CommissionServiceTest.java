package com.fksoft.erp.domain.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.commission.exception.CommissionAlreadyExistsException;
import com.fksoft.erp.domain.commission.exception.CommissionNotFoundException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNoAmountException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNoResponsibleException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNotClosedException;
import com.fksoft.erp.domain.commission.exception.CommissionSourceOrderAccessDeniedException;
import com.fksoft.erp.domain.commission.exception.CommissionSourceOrderNotFoundException;
import com.fksoft.erp.domain.commission.exception.NoApplicableCommissionRuleException;
import com.fksoft.erp.domain.commission.model.Commission;
import com.fksoft.erp.domain.commission.model.CommissionBasis;
import com.fksoft.erp.domain.commission.model.CommissionRule;
import com.fksoft.erp.domain.commission.model.CommissionRuleData;
import com.fksoft.erp.domain.commission.model.CommissionStatus;
import com.fksoft.erp.domain.commission.model.CommissionTargetType;
import com.fksoft.erp.domain.commission.repository.CommissionRepository;
import com.fksoft.erp.domain.commission.repository.CommissionRuleRepository;
import com.fksoft.erp.domain.commission.service.CommissionService;
import com.fksoft.erp.domain.commission.service.data.CommissionDetail;
import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.repository.ReceivableRepository;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
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

/** Unit tests of the Commission generation Application Service with the repositories and policy mocked. */
@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Mock
    private CommissionRepository commissions;

    @Mock
    private CommissionRuleRepository rules;

    @Mock
    private ReceivableRepository receivables;

    @Mock
    private CommercialOrderRepository orders;

    @Mock
    private OrderAccessPolicy orderAccessPolicy;

    @Mock
    private UserRepository users;

    @InjectMocks
    private CommissionService service;

    private final UUID orderId = UUID.randomUUID();
    private final UUID beneficiary = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private CommercialOrder order(UUID responsible, BigDecimal total, CommercialOrderStatus status) {
        CommercialOrder order = mock(CommercialOrder.class);
        lenient().when(order.id()).thenReturn(orderId);
        lenient().when(order.status()).thenReturn(status);
        lenient().when(order.responsiblePersonId()).thenReturn(responsible);
        lenient().when(order.total()).thenReturn(total);
        lenient().when(order.proposalId()).thenReturn(UUID.randomUUID());
        lenient().when(order.opportunityId()).thenReturn(UUID.randomUUID());
        lenient().when(order.leadId()).thenReturn(UUID.randomUUID());
        lenient().when(order.number()).thenReturn(7L);
        return order;
    }

    private CommercialOrder closedOrder() {
        return order(beneficiary, new BigDecimal("1000.00"), CommercialOrderStatus.PENDING_BOOKING);
    }

    private CommissionRule rule(CommissionTargetType type, UUID targetUserId, LocalDate start, LocalDate end) {
        return CommissionRule.create(
                new CommissionRuleData("R", new BigDecimal("5"), type, targetUserId, start, end, null), userId);
    }

    private void visibleOrder(CommercialOrder order) {
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(order, userId, true, false)).thenReturn(true);
        when(commissions.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.empty());
    }

    private Commission generatedCommission() {
        CommissionRule rule = rule(CommissionTargetType.COMMERCIAL_RESPONSIBLE, null, START, null);
        return Commission.generate(
                closedOrder(), rule, CommissionBasis.COMMERCIAL_AMOUNT, new BigDecimal("1000.00"), userId);
    }

    @Test
    void generatesAForecastFromTheCommercialTotalWhenThereIsNoReceivable() {
        CommercialOrder order = closedOrder();
        visibleOrder(order);
        when(rules.findByActiveTrueOrderByCreatedAtDesc())
                .thenReturn(List.of(rule(CommissionTargetType.COMMERCIAL_RESPONSIBLE, null, START, null)));
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.empty());

        UUID id = service.generate(orderId, userId, true, false);

        ArgumentCaptor<Commission> saved = ArgumentCaptor.forClass(Commission.class);
        verify(commissions).save(saved.capture());
        assertThat(id).isEqualTo(saved.getValue().id());
        assertThat(saved.getValue().basisType()).isEqualTo(CommissionBasis.COMMERCIAL_AMOUNT);
        assertThat(saved.getValue().baseAmount()).isEqualByComparingTo("1000.00");
        assertThat(saved.getValue().amount()).isEqualByComparingTo("50.00");
        assertThat(saved.getValue().status()).isEqualTo(CommissionStatus.EXPECTED);
        assertThat(saved.getValue().beneficiaryUserId()).isEqualTo(beneficiary);
    }

    @Test
    void generatesFromTheReceivedAmountWhenTheReceivableHasPayments() {
        CommercialOrder order = closedOrder();
        visibleOrder(order);
        when(rules.findByActiveTrueOrderByCreatedAtDesc())
                .thenReturn(List.of(rule(CommissionTargetType.COMMERCIAL_RESPONSIBLE, null, START, null)));
        Receivable receivable = mock(Receivable.class);
        when(receivable.amountPaid()).thenReturn(new BigDecimal("400.00"));
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.of(receivable));

        service.generate(orderId, userId, true, false);

        ArgumentCaptor<Commission> saved = ArgumentCaptor.forClass(Commission.class);
        verify(commissions).save(saved.capture());
        assertThat(saved.getValue().basisType()).isEqualTo(CommissionBasis.RECEIVED_AMOUNT);
        assertThat(saved.getValue().baseAmount()).isEqualByComparingTo("400.00");
        assertThat(saved.getValue().amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void treatsAReceivableWithoutPaymentsAsAForecast() {
        CommercialOrder order = closedOrder();
        visibleOrder(order);
        when(rules.findByActiveTrueOrderByCreatedAtDesc())
                .thenReturn(List.of(rule(CommissionTargetType.COMMERCIAL_RESPONSIBLE, null, START, null)));
        Receivable receivable = mock(Receivable.class);
        when(receivable.amountPaid()).thenReturn(BigDecimal.ZERO);
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.of(receivable));

        service.generate(orderId, userId, true, false);

        ArgumentCaptor<Commission> saved = ArgumentCaptor.forClass(Commission.class);
        verify(commissions).save(saved.capture());
        assertThat(saved.getValue().basisType()).isEqualTo(CommissionBasis.COMMERCIAL_AMOUNT);
        assertThat(saved.getValue().baseAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void prefersAUserSpecificRuleOverTheGenericResponsibleRule() {
        CommercialOrder order = closedOrder();
        visibleOrder(order);
        CommissionRule generic = rule(CommissionTargetType.COMMERCIAL_RESPONSIBLE, null, START, null);
        CommissionRule specific = rule(CommissionTargetType.SELLER, beneficiary, START, null);
        when(rules.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of(specific, generic));
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.empty());

        service.generate(orderId, userId, true, false);

        ArgumentCaptor<Commission> saved = ArgumentCaptor.forClass(Commission.class);
        verify(commissions).save(saved.capture());
        assertThat(saved.getValue().ruleId()).isEqualTo(specific.id());
    }

    @Test
    void fallsBackToTheGenericResponsibleRuleWhenNoUserSpecificOneApplies() {
        CommercialOrder order = closedOrder();
        visibleOrder(order);
        CommissionRule generic = rule(CommissionTargetType.COMMERCIAL_RESPONSIBLE, null, START, null);
        when(rules.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of(generic));
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.empty());

        service.generate(orderId, userId, true, false);

        ArgumentCaptor<Commission> saved = ArgumentCaptor.forClass(Commission.class);
        verify(commissions).save(saved.capture());
        assertThat(saved.getValue().ruleId()).isEqualTo(generic.id());
    }

    @Test
    void ignoresAnOutOfWindowRule() {
        CommercialOrder order = closedOrder();
        visibleOrder(order);
        // The only rule starts in the far future → not yet applicable.
        when(rules.findByActiveTrueOrderByCreatedAtDesc())
                .thenReturn(List.of(
                        rule(CommissionTargetType.COMMERCIAL_RESPONSIBLE, null, LocalDate.of(2999, 1, 1), null)));

        assertThatThrownBy(() -> service.generate(orderId, userId, true, false))
                .isInstanceOf(NoApplicableCommissionRuleException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void doesNotAutoMatchAGenericSellerRule() {
        CommercialOrder order = closedOrder();
        visibleOrder(order);
        // A generic SELLER rule (no targetUserId) is not auto-matched — there is no user-role model.
        when(rules.findByActiveTrueOrderByCreatedAtDesc())
                .thenReturn(List.of(rule(CommissionTargetType.SELLER, null, START, null)));

        assertThatThrownBy(() -> service.generate(orderId, userId, true, false))
                .isInstanceOf(NoApplicableCommissionRuleException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void rejectsAnUnknownSourceOrder() {
        when(orders.findById(orderId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generate(orderId, userId, true, false))
                .isInstanceOf(CommissionSourceOrderNotFoundException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void rejectsAnOrderTheCallerCannotSee() {
        CommercialOrder order = closedOrder();
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(order, userId, false, false)).thenReturn(false);
        assertThatThrownBy(() -> service.generate(orderId, userId, false, false))
                .isInstanceOf(CommissionSourceOrderAccessDeniedException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void rejectsACancelledOrder() {
        CommercialOrder order = order(beneficiary, new BigDecimal("1000.00"), CommercialOrderStatus.CANCELLED);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(order, userId, true, false)).thenReturn(true);
        assertThatThrownBy(() -> service.generate(orderId, userId, true, false))
                .isInstanceOf(CommissionOrderNotClosedException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void rejectsAnOrderWithoutAResponsible() {
        CommercialOrder order = order(null, new BigDecimal("1000.00"), CommercialOrderStatus.PENDING_BOOKING);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(order, userId, true, false)).thenReturn(true);
        assertThatThrownBy(() -> service.generate(orderId, userId, true, false))
                .isInstanceOf(CommissionOrderNoResponsibleException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void rejectsAnOrderWithoutAPositiveAmount() {
        CommercialOrder order = order(beneficiary, BigDecimal.ZERO, CommercialOrderStatus.PENDING_BOOKING);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(order, userId, true, false)).thenReturn(true);
        assertThatThrownBy(() -> service.generate(orderId, userId, true, false))
                .isInstanceOf(CommissionOrderNoAmountException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void rejectsAnOrderThatAlreadyHasAnActiveCommission() {
        CommercialOrder order = closedOrder();
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderAccessPolicy.canSee(order, userId, true, false)).thenReturn(true);
        Commission existing = generatedCommission();
        when(commissions.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.generate(orderId, userId, true, false))
                .isInstanceOf(CommissionAlreadyExistsException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void detailReturnsTheReadModelWithResolvedNames() {
        Commission commission = generatedCommission();
        CommercialOrder detailOrder = closedOrder();
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(orders.findById(commission.commercialOrderId())).thenReturn(Optional.of(detailOrder));
        User user = mock(User.class);
        when(user.username()).thenReturn("vendedor");
        when(users.findById(commission.beneficiaryUserId())).thenReturn(Optional.of(user));
        when(rules.findById(commission.ruleId())).thenReturn(Optional.empty());

        var detail = service.detail(commission.id());

        assertThat(detail.id()).isEqualTo(commission.id());
        assertThat(detail.orderNumber()).isEqualTo(7L);
        assertThat(detail.beneficiaryName()).isEqualTo("vendedor");
        assertThat(detail.status()).isEqualTo("EXPECTED");
        assertThat(detail.amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void detailRejectsAnUnknownCommission() {
        UUID id = UUID.randomUUID();
        when(commissions.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(id)).isInstanceOf(CommissionNotFoundException.class);
    }

    @Test
    void generatesAnEligibleCommissionWhenTheReceivableIsAlreadyFullyPaid() {
        CommercialOrder order = closedOrder();
        visibleOrder(order);
        when(rules.findByActiveTrueOrderByCreatedAtDesc())
                .thenReturn(List.of(rule(CommissionTargetType.COMMERCIAL_RESPONSIBLE, null, START, null)));
        UUID receivableId = UUID.randomUUID();
        Receivable receivable = mock(Receivable.class);
        when(receivable.amountPaid()).thenReturn(new BigDecimal("1000.00"));
        when(receivable.status()).thenReturn(ReceivableStatus.PAID);
        when(receivable.id()).thenReturn(receivableId);
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.of(receivable));

        service.generate(orderId, userId, true, false);

        ArgumentCaptor<Commission> saved = ArgumentCaptor.forClass(Commission.class);
        verify(commissions).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(CommissionStatus.ELIGIBLE);
        assertThat(saved.getValue().eligibleAt()).isNotNull();
        assertThat(saved.getValue().receivableId()).isEqualTo(receivableId);
    }

    @Test
    void staysExpectedWhenTheReceivableIsOnlyPartiallyPaidAtGeneration() {
        CommercialOrder order = closedOrder();
        visibleOrder(order);
        when(rules.findByActiveTrueOrderByCreatedAtDesc())
                .thenReturn(List.of(rule(CommissionTargetType.COMMERCIAL_RESPONSIBLE, null, START, null)));
        Receivable receivable = mock(Receivable.class);
        when(receivable.amountPaid()).thenReturn(new BigDecimal("300.00"));
        when(receivable.status()).thenReturn(ReceivableStatus.PARTIALLY_PAID);
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.of(receivable));

        service.generate(orderId, userId, true, false);

        ArgumentCaptor<Commission> saved = ArgumentCaptor.forClass(Commission.class);
        verify(commissions).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(CommissionStatus.EXPECTED);
        assertThat(saved.getValue().eligibleAt()).isNull();
    }

    @Test
    void byOrderReturnsTheActiveCommission() {
        Commission commission = generatedCommission();
        CommercialOrder detailOrder = closedOrder();
        when(commissions.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.of(commission));
        when(orders.findById(orderId)).thenReturn(Optional.of(detailOrder));
        when(users.findById(any())).thenReturn(Optional.empty());
        when(rules.findById(any())).thenReturn(Optional.empty());

        List<CommissionDetail> result = service.byOrder(orderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(commission.id());
        assertThat(result.get(0).status()).isEqualTo("EXPECTED");
    }

    @Test
    void byOrderReturnsEmptyWhenThereIsNoCommission() {
        when(commissions.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.empty());
        assertThat(service.byOrder(orderId)).isEmpty();
    }
}
