package com.fksoft.erp.domain.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.commission.exception.CommissionAccessDeniedException;
import com.fksoft.erp.domain.commission.exception.CommissionAlreadyExistsException;
import com.fksoft.erp.domain.commission.exception.CommissionNotCancellableException;
import com.fksoft.erp.domain.commission.exception.CommissionNotEligibleException;
import com.fksoft.erp.domain.commission.exception.CommissionNotFoundException;
import com.fksoft.erp.domain.commission.exception.CommissionNotRejectableException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNoAmountException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNoResponsibleException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNotClosedException;
import com.fksoft.erp.domain.commission.exception.CommissionResolutionReasonNotAvailableException;
import com.fksoft.erp.domain.commission.exception.CommissionSelfApprovalNotAllowedException;
import com.fksoft.erp.domain.commission.exception.CommissionSourceOrderAccessDeniedException;
import com.fksoft.erp.domain.commission.exception.CommissionSourceOrderNotFoundException;
import com.fksoft.erp.domain.commission.exception.NoApplicableCommissionRuleException;
import com.fksoft.erp.domain.commission.model.Commission;
import com.fksoft.erp.domain.commission.model.CommissionBasis;
import com.fksoft.erp.domain.commission.model.CommissionResolutionReason;
import com.fksoft.erp.domain.commission.model.CommissionRule;
import com.fksoft.erp.domain.commission.model.CommissionRuleData;
import com.fksoft.erp.domain.commission.model.CommissionStatus;
import com.fksoft.erp.domain.commission.model.CommissionTargetType;
import com.fksoft.erp.domain.commission.repository.CommissionRepository;
import com.fksoft.erp.domain.commission.repository.CommissionResolutionReasonRepository;
import com.fksoft.erp.domain.commission.repository.CommissionRuleRepository;
import com.fksoft.erp.domain.commission.service.CommissionService;
import com.fksoft.erp.domain.commission.service.data.CommissionSearchCriteria;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.repository.ReceivableRepository;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.service.OrderAccessPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
    private com.fksoft.erp.domain.sales.repository.ProposalRepository proposals;

    @Mock
    private com.fksoft.erp.domain.crm.repository.OpportunityRepository opportunities;

    @Mock
    private OrderAccessPolicy orderAccessPolicy;

    @Mock
    private com.fksoft.erp.domain.commission.service.CommissionAccessPolicy accessPolicy;

    @Mock
    private UserRepository users;

    @Mock
    private CommissionResolutionReasonRepository resolutionReasons;

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
    void detailReturnsTheEnrichedReadModelWhenVisible() {
        Commission commission = generatedCommission();
        CommercialOrder detailOrder = closedOrder();
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, true)).thenReturn(true);
        when(orders.findById(commission.commercialOrderId())).thenReturn(Optional.of(detailOrder));
        User beneficiaryUser = mock(User.class);
        when(beneficiaryUser.username()).thenReturn("vendedor");
        when(users.findById(beneficiary)).thenReturn(Optional.of(beneficiaryUser));
        User creator = mock(User.class);
        when(creator.username()).thenReturn("comercial");
        when(users.findById(userId)).thenReturn(Optional.of(creator)); // the generator (createdBy)
        when(rules.findById(commission.ruleId())).thenReturn(Optional.empty());
        Proposal proposal = mock(Proposal.class);
        when(proposal.title()).thenReturn("Proposta Aurora");
        when(proposals.findById(commission.proposalId())).thenReturn(Optional.of(proposal));
        Opportunity opportunity = mock(Opportunity.class);
        when(opportunity.name()).thenReturn("Aurora");
        when(opportunities.findById(commission.opportunityId())).thenReturn(Optional.of(opportunity));
        Receivable receivable = mock(Receivable.class);
        when(receivable.id()).thenReturn(UUID.randomUUID());
        when(receivable.status()).thenReturn(ReceivableStatus.PAID);
        when(receivables.findFirstByCommercialOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.of(receivable));

        var detail = service.detail(commission.id(), userId, true);

        assertThat(detail.id()).isEqualTo(commission.id());
        assertThat(detail.orderNumber()).isEqualTo(7L);
        assertThat(detail.beneficiaryName()).isEqualTo("vendedor");
        assertThat(detail.createdByName()).isEqualTo("comercial");
        assertThat(detail.proposalReference()).isEqualTo("Proposta Aurora");
        assertThat(detail.opportunityReference()).isEqualTo("Aurora");
        assertThat(detail.receivableStatus()).isEqualTo("PAID");
        assertThat(detail.status()).isEqualTo("EXPECTED");
        assertThat(detail.amount()).isEqualByComparingTo("50.00");
        // The forward-looking lifecycle stamps are null until their slices populate them.
        assertThat(detail.approvedAt()).isNull();
        assertThat(detail.paidAt()).isNull();
    }

    @Test
    void detailDeniesACommissionTheCallerCannotSee() {
        Commission commission = generatedCommission();
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, false)).thenReturn(false);
        assertThatThrownBy(() -> service.detail(commission.id(), userId, false))
                .isInstanceOf(CommissionAccessDeniedException.class);
    }

    @Test
    void detailRejectsAnUnknownCommission() {
        UUID id = UUID.randomUUID();
        when(commissions.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(id, userId, true)).isInstanceOf(CommissionNotFoundException.class);
    }

    // An ELIGIBLE commission (beneficiary = `beneficiary`, createdBy = `userId`), ready for approval.
    private Commission eligibleCommission() {
        Commission commission = generatedCommission();
        commission.markEligible(UUID.randomUUID(), Instant.now());
        return commission;
    }

    @Test
    void approveTransitionsTheEligibleCommissionAndSavesIt() {
        Commission commission = eligibleCommission();
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, true)).thenReturn(true);

        var detail = service.approve(commission.id(), userId, true, "ok"); // approver userId != beneficiary

        assertThat(commission.status()).isEqualTo(CommissionStatus.APPROVED);
        assertThat(commission.approvedBy()).isEqualTo(userId);
        assertThat(commission.approvalNotes()).isEqualTo("ok");
        assertThat(detail.status()).isEqualTo("APPROVED");
        verify(commissions).save(commission);
    }

    @Test
    void approveRejectsSelfApprovalByTheBeneficiary() {
        Commission commission = eligibleCommission();
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, beneficiary, true)).thenReturn(true);

        // The approver IS the beneficiary.
        assertThatThrownBy(() -> service.approve(commission.id(), beneficiary, true, null))
                .isInstanceOf(CommissionSelfApprovalNotAllowedException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void approveDeniesACommissionTheApproverCannotSee() {
        Commission commission = eligibleCommission();
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, false)).thenReturn(false);

        assertThatThrownBy(() -> service.approve(commission.id(), userId, false, null))
                .isInstanceOf(CommissionAccessDeniedException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void approvePropagatesNotEligibleForANonEligibleCommission() {
        Commission commission = generatedCommission(); // EXPECTED
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, true)).thenReturn(true);

        assertThatThrownBy(() -> service.approve(commission.id(), userId, true, null))
                .isInstanceOf(CommissionNotEligibleException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void approveRejectsAnUnknownCommission() {
        UUID id = UUID.randomUUID();
        when(commissions.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approve(id, userId, true, null))
                .isInstanceOf(CommissionNotFoundException.class);
    }

    private Commission approvedCommission() {
        Commission commission = eligibleCommission();
        commission.approve(userId, null, Instant.now());
        return commission;
    }

    private UUID stubActiveReason() {
        UUID reasonId = UUID.randomUUID();
        when(resolutionReasons.findById(reasonId))
                .thenReturn(Optional.of(CommissionResolutionReason.create("OTHER", "Outro", 1)));
        return reasonId;
    }

    @Test
    void rejectTransitionsTheEligibleCommissionAndSavesIt() {
        Commission commission = eligibleCommission();
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, true)).thenReturn(true);
        UUID reasonId = stubActiveReason();

        var detail = service.reject(commission.id(), reasonId, "duplicada", userId, true);

        assertThat(commission.status()).isEqualTo(CommissionStatus.REJECTED);
        assertThat(commission.resolvedBy()).isEqualTo(userId);
        assertThat(commission.resolutionNote()).isEqualTo("duplicada");
        assertThat(commission.resolutionReason().label()).isEqualTo("Outro");
        assertThat(detail.status()).isEqualTo("REJECTED");
        assertThat(detail.resolutionReason()).isEqualTo("Outro");
        verify(commissions).save(commission);
    }

    @Test
    void rejectDeniesACommissionTheCallerCannotSee() {
        Commission commission = eligibleCommission();
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, false)).thenReturn(false);

        assertThatThrownBy(() -> service.reject(commission.id(), UUID.randomUUID(), null, userId, false))
                .isInstanceOf(CommissionAccessDeniedException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void rejectRejectsAnUnknownReason() {
        Commission commission = eligibleCommission();
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, true)).thenReturn(true);
        UUID reasonId = UUID.randomUUID();
        when(resolutionReasons.findById(reasonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(commission.id(), reasonId, null, userId, true))
                .isInstanceOf(CommissionResolutionReasonNotAvailableException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void rejectPropagatesNotRejectableForANonEligibleCommission() {
        Commission commission = generatedCommission(); // EXPECTED
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, true)).thenReturn(true);
        UUID reasonId = stubActiveReason();

        assertThatThrownBy(() -> service.reject(commission.id(), reasonId, null, userId, true))
                .isInstanceOf(CommissionNotRejectableException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    void rejectRejectsAnUnknownCommission() {
        UUID id = UUID.randomUUID();
        when(commissions.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reject(id, UUID.randomUUID(), null, userId, true))
                .isInstanceOf(CommissionNotFoundException.class);
    }

    @Test
    void cancelTransitionsAnExpectedCommissionAndSavesIt() {
        Commission commission = generatedCommission(); // EXPECTED
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, true)).thenReturn(true);
        UUID reasonId = stubActiveReason();

        var detail = service.cancel(commission.id(), reasonId, "engano", userId, true);

        assertThat(commission.status()).isEqualTo(CommissionStatus.CANCELLED);
        assertThat(commission.resolvedBy()).isEqualTo(userId);
        assertThat(detail.status()).isEqualTo("CANCELLED");
        verify(commissions).save(commission);
    }

    @Test
    void cancelTransitionsAnApprovedCommissionAndSavesIt() {
        Commission commission = approvedCommission();
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, true)).thenReturn(true);
        UUID reasonId = stubActiveReason();

        service.cancel(commission.id(), reasonId, null, userId, true);

        assertThat(commission.status()).isEqualTo(CommissionStatus.CANCELLED);
        verify(commissions).save(commission);
    }

    @Test
    void cancelPropagatesNotCancellableForAnEligibleCommission() {
        Commission commission = eligibleCommission(); // ELIGIBLE → use reject, not cancel
        when(commissions.findById(commission.id())).thenReturn(Optional.of(commission));
        when(accessPolicy.canSee(commission, userId, true)).thenReturn(true);
        UUID reasonId = stubActiveReason();

        assertThatThrownBy(() -> service.cancel(commission.id(), reasonId, null, userId, true))
                .isInstanceOf(CommissionNotCancellableException.class);
        verify(commissions, never()).save(any());
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
    void listAppliesTheVisibilitySpecificationAndResolvesTheReferences() {
        Commission commission = generatedCommission();
        Specification<Commission> visible = (root, q, cb) -> cb.conjunction();
        when(accessPolicy.visibleTo(userId, true)).thenReturn(visible);
        when(commissions.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(commission)));
        CommercialOrder listOrder = closedOrder();
        when(orders.findAllById(anySet())).thenReturn(List.of(listOrder));
        User user = mock(User.class);
        when(user.id()).thenReturn(beneficiary);
        when(user.username()).thenReturn("vendedor");
        when(users.findAllById(anySet())).thenReturn(List.of(user));
        when(rules.findAllById(anySet())).thenReturn(List.of());
        when(proposals.findAllById(anySet())).thenReturn(List.of());
        when(opportunities.findAllById(anySet())).thenReturn(List.of());
        Receivable receivable = mock(Receivable.class);
        when(receivable.commercialOrderId()).thenReturn(orderId);
        when(receivable.status()).thenReturn(ReceivableStatus.PAID);
        when(receivables.findByCommercialOrderIdIn(anySet())).thenReturn(List.of(receivable));

        CommissionSearchCriteria criteria = new CommissionSearchCriteria(
                Set.of(), null, null, null, null, null, null, null, null, null, null, null, null);
        var page = service.list(criteria, Pageable.unpaged(), userId, true);

        assertThat(page.getContent()).hasSize(1);
        var item = page.getContent().get(0);
        assertThat(item.id()).isEqualTo(commission.id());
        assertThat(item.beneficiaryName()).isEqualTo("vendedor");
        assertThat(item.orderNumber()).isEqualTo(7L);
        assertThat(item.status()).isEqualTo("EXPECTED");
        assertThat(item.receivableStatus()).isEqualTo("PAID");
        verify(accessPolicy).visibleTo(userId, true);
    }
}
