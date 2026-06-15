package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeadAssignmentPolicyTest {

    private final LeadAssignmentPolicy policy = new LeadAssignmentPolicy();
    private final UUID actor = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void fullAuthorityMayAssignAnyoneIncludingUnassign() {
        assertThat(policy.canAssign(actor, other, true)).isTrue();
        assertThat(policy.canAssign(actor, actor, true)).isTrue();
        assertThat(policy.canAssign(actor, null, true)).isTrue();
    }

    @Test
    void withoutAuthorityMayOnlySelfClaim() {
        assertThat(policy.canAssign(actor, actor, false)).isTrue();
    }

    @Test
    void withoutAuthorityCannotAssignAnotherUserOrUnassign() {
        assertThat(policy.canAssign(actor, other, false)).isFalse();
        assertThat(policy.canAssign(actor, null, false)).isFalse();
    }
}
