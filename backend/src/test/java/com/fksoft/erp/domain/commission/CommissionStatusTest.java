package com.fksoft.erp.domain.commission;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.erp.domain.commission.model.CommissionStatus;
import org.junit.jupiter.api.Test;

/** Unit tests of the Commission lifecycle's active-status set (the "one active Commission per Order" rule). */
class CommissionStatusTest {

    @Test
    void activeExcludesTheTerminalDeadStates() {
        assertThat(CommissionStatus.active())
                .containsExactlyInAnyOrder(
                        CommissionStatus.EXPECTED,
                        CommissionStatus.ELIGIBLE,
                        CommissionStatus.APPROVED,
                        CommissionStatus.PAID)
                .doesNotContain(CommissionStatus.REJECTED, CommissionStatus.CANCELLED);
    }
}
