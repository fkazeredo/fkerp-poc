package com.fksoft.erp.domain.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.commission.model.Commission;
import com.fksoft.erp.domain.commission.model.CommissionStatus;
import com.fksoft.erp.domain.commission.service.data.CommissionOperationalSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests of the operational-summary grouping: per-status and per-beneficiary counts/totals, ordering and edges. */
class CommissionOperationalSummaryTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();

    private Commission commission(CommissionStatus status, UUID beneficiary, String amount) {
        Commission c = mock(Commission.class);
        when(c.status()).thenReturn(status);
        when(c.beneficiaryUserId()).thenReturn(beneficiary);
        when(c.amount()).thenReturn(new BigDecimal(amount));
        return c;
    }

    @Test
    void groupsByStatusAndBeneficiaryWithTotals() {
        List<Commission> rows = List.of(
                commission(CommissionStatus.EXPECTED, ANA, "25.00"),
                commission(CommissionStatus.EXPECTED, BRUNO, "10.00"),
                commission(CommissionStatus.PAID, ANA, "30.00"));

        CommissionOperationalSummary summary =
                CommissionOperationalSummary.of(rows, Map.of(ANA, "ana", BRUNO, "bruno"));

        assertThat(summary.totalCount()).isEqualTo(3);
        assertThat(summary.totalAmount()).isEqualByComparingTo("65.00");

        // By status: EXPECTED (2 = 35.00) and PAID (1 = 30.00), in lifecycle order, absent statuses skipped.
        assertThat(summary.byStatus()).hasSize(2);
        assertThat(summary.byStatus().get(0).status()).isEqualTo(CommissionStatus.EXPECTED);
        assertThat(summary.byStatus().get(0).count()).isEqualTo(2);
        assertThat(summary.byStatus().get(0).totalAmount()).isEqualByComparingTo("35.00");
        assertThat(summary.byStatus().get(1).status()).isEqualTo(CommissionStatus.PAID);
        assertThat(summary.byStatus().get(1).totalAmount()).isEqualByComparingTo("30.00");

        // By beneficiary: ordered by name — ana (2 = 55.00) then bruno (1 = 10.00).
        assertThat(summary.byBeneficiary()).hasSize(2);
        assertThat(summary.byBeneficiary().get(0).beneficiaryName()).isEqualTo("ana");
        assertThat(summary.byBeneficiary().get(0).count()).isEqualTo(2);
        assertThat(summary.byBeneficiary().get(0).totalAmount()).isEqualByComparingTo("55.00");
        assertThat(summary.byBeneficiary().get(1).beneficiaryName()).isEqualTo("bruno");
        assertThat(summary.byBeneficiary().get(1).totalAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void emptyRowsGiveEmptyGroupsAndZeroTotals() {
        CommissionOperationalSummary summary = CommissionOperationalSummary.of(List.of(), Map.of());

        assertThat(summary.totalCount()).isZero();
        assertThat(summary.totalAmount()).isEqualByComparingTo("0");
        assertThat(summary.byStatus()).isEmpty();
        assertThat(summary.byBeneficiary()).isEmpty();
    }

    @Test
    void emitsStatusesInLifecycleOrder() {
        List<Commission> rows = List.of(
                commission(CommissionStatus.PAID, ANA, "1.00"),
                commission(CommissionStatus.EXPECTED, ANA, "1.00"),
                commission(CommissionStatus.APPROVED, ANA, "1.00"));

        CommissionOperationalSummary summary = CommissionOperationalSummary.of(rows, Map.of(ANA, "ana"));

        assertThat(summary.byStatus())
                .extracting(CommissionOperationalSummary.StatusTotal::status)
                .containsExactly(CommissionStatus.EXPECTED, CommissionStatus.APPROVED, CommissionStatus.PAID);
    }
}
