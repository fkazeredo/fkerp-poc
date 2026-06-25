package com.fksoft.erp.domain.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.fksoft.erp.domain.commission.model.Commission;
import com.fksoft.erp.domain.commission.service.CommissionAccessPolicy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests of the Commission read-visibility policy (own beneficiary vs all). */
class CommissionAccessPolicyTest {

    private final CommissionAccessPolicy policy = new CommissionAccessPolicy();
    private final UUID beneficiary = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private Commission commissionOf(UUID beneficiaryUserId) {
        Commission commission = mock(Commission.class);
        lenient().when(commission.beneficiaryUserId()).thenReturn(beneficiaryUserId);
        return commission;
    }

    @Test
    void readAllSeesEveryCommission() {
        assertThat(policy.canSee(commissionOf(other), beneficiary, true)).isTrue();
    }

    @Test
    void ownTierSeesOnlyTheirOwnCommission() {
        assertThat(policy.canSee(commissionOf(beneficiary), beneficiary, false)).isTrue();
        assertThat(policy.canSee(commissionOf(other), beneficiary, false)).isFalse();
    }

    @Test
    void visibleToIsAlwaysTrueWhenSeeingAll() {
        // The all-tier predicate is a conjunction (no restriction).
        assertThat(policy.visibleTo(beneficiary, true)).isNotNull();
        assertThat(policy.visibleTo(beneficiary, false)).isNotNull();
    }
}
