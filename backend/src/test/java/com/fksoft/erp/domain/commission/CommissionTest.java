package com.fksoft.erp.domain.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.fksoft.erp.domain.commission.model.Commission;
import com.fksoft.erp.domain.commission.model.CommissionBasis;
import com.fksoft.erp.domain.commission.model.CommissionRule;
import com.fksoft.erp.domain.commission.model.CommissionRuleData;
import com.fksoft.erp.domain.commission.model.CommissionStatus;
import com.fksoft.erp.domain.commission.model.CommissionTargetType;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests of the Commission aggregate: the forecast amount calculation and the preserved snapshot. */
class CommissionTest {

    private final UUID orderId = UUID.randomUUID();
    private final UUID proposalId = UUID.randomUUID();
    private final UUID opportunityId = UUID.randomUUID();
    private final UUID leadId = UUID.randomUUID();
    private final UUID beneficiary = UUID.randomUUID();
    private final UUID creator = UUID.randomUUID();

    private CommercialOrder order(BigDecimal total) {
        CommercialOrder order = mock(CommercialOrder.class);
        lenient().when(order.id()).thenReturn(orderId);
        lenient().when(order.proposalId()).thenReturn(proposalId);
        lenient().when(order.opportunityId()).thenReturn(opportunityId);
        lenient().when(order.leadId()).thenReturn(leadId);
        lenient().when(order.responsiblePersonId()).thenReturn(beneficiary);
        lenient().when(order.total()).thenReturn(total);
        return order;
    }

    private CommissionRule rule(String percentage) {
        return CommissionRule.create(
                new CommissionRuleData(
                        "Padrão",
                        new BigDecimal(percentage),
                        CommissionTargetType.COMMERCIAL_RESPONSIBLE,
                        null,
                        LocalDate.of(2026, 1, 1),
                        null,
                        null),
                creator);
    }

    @Test
    void generatesAnExpectedCommissionFromTheCommercialAmount() {
        Commission commission = Commission.generate(
                order(new BigDecimal("1000.00")),
                rule("5"),
                CommissionBasis.COMMERCIAL_AMOUNT,
                new BigDecimal("1000.00"),
                creator);

        assertThat(commission.status()).isEqualTo(CommissionStatus.EXPECTED);
        assertThat(commission.basisType()).isEqualTo(CommissionBasis.COMMERCIAL_AMOUNT);
        assertThat(commission.baseAmount()).isEqualByComparingTo("1000.00");
        assertThat(commission.rulePercentage()).isEqualByComparingTo("5.00");
        assertThat(commission.amount()).isEqualByComparingTo("50.00");
        // The commercial origin and the beneficiary are preserved (snapshot).
        assertThat(commission.commercialOrderId()).isEqualTo(orderId);
        assertThat(commission.proposalId()).isEqualTo(proposalId);
        assertThat(commission.opportunityId()).isEqualTo(opportunityId);
        assertThat(commission.leadId()).isEqualTo(leadId);
        assertThat(commission.beneficiaryUserId()).isEqualTo(beneficiary);
        assertThat(commission.createdBy()).isEqualTo(creator);
    }

    @Test
    void generatesFromTheReceivedAmountWhenAvailable() {
        Commission commission = Commission.generate(
                order(new BigDecimal("1000.00")),
                rule("5"),
                CommissionBasis.RECEIVED_AMOUNT,
                new BigDecimal("400.00"),
                creator);

        assertThat(commission.basisType()).isEqualTo(CommissionBasis.RECEIVED_AMOUNT);
        assertThat(commission.baseAmount()).isEqualByComparingTo("400.00");
        assertThat(commission.amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void roundsTheAmountHalfUpToTwoDecimals() {
        // 10.00 × 2.55% = 0.255 → HALF_UP at scale 2 = 0.26.
        Commission commission = Commission.generate(
                order(new BigDecimal("10.00")),
                rule("2.55"),
                CommissionBasis.COMMERCIAL_AMOUNT,
                new BigDecimal("10.00"),
                creator);

        assertThat(commission.amount()).isEqualByComparingTo("0.26");
    }

    @Test
    void markEligibleTransitionsFromExpectedAndRecordsTheEvidence() {
        Commission commission = Commission.generate(
                order(new BigDecimal("1000.00")),
                rule("5"),
                CommissionBasis.COMMERCIAL_AMOUNT,
                new BigDecimal("1000.00"),
                creator);
        UUID receivableId = UUID.randomUUID();
        Instant when = Instant.parse("2026-06-25T10:00:00Z");

        boolean changed = commission.markEligible(receivableId, when);

        assertThat(changed).isTrue();
        assertThat(commission.status()).isEqualTo(CommissionStatus.ELIGIBLE);
        assertThat(commission.eligibleAt()).isEqualTo(when);
        assertThat(commission.receivableId()).isEqualTo(receivableId);
    }

    @Test
    void markEligibleIsAnIdempotentNoOpWhenNotExpected() {
        Commission commission = Commission.generate(
                order(new BigDecimal("1000.00")),
                rule("5"),
                CommissionBasis.COMMERCIAL_AMOUNT,
                new BigDecimal("1000.00"),
                creator);
        UUID firstReceivable = UUID.randomUUID();
        Instant firstWhen = Instant.parse("2026-06-25T10:00:00Z");
        commission.markEligible(firstReceivable, firstWhen); // EXPECTED -> ELIGIBLE

        // A second call (e.g. a repeated PAID event) leaves the already-eligible commission untouched.
        boolean changedAgain = commission.markEligible(UUID.randomUUID(), Instant.parse("2026-07-01T00:00:00Z"));

        assertThat(changedAgain).isFalse();
        assertThat(commission.status()).isEqualTo(CommissionStatus.ELIGIBLE);
        assertThat(commission.eligibleAt()).isEqualTo(firstWhen);
        assertThat(commission.receivableId()).isEqualTo(firstReceivable);
    }
}
