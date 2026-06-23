package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.service.LeadAccessPolicy;
import com.fksoft.erp.domain.crm.service.data.RegisterLeadCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The three escalating read tiers: own-only, own + unassigned pool, and all. */
class LeadAccessPolicyTest {

    private final LeadAccessPolicy policy = new LeadAccessPolicy();
    private final Origin origin = Origin.create("WEBSITE", "Website", 1);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private Lead leadOwnedBy(UUID responsible) {
        return Lead.register(
                new RegisterLeadCommand("X", "11999999999", null, null, UUID.randomUUID(), responsible, null),
                origin,
                UUID.randomUUID());
    }

    @Test
    void ownOnlySeesOwnButNotUnassignedNorOthers() {
        assertThat(policy.canSee(leadOwnedBy(me), me, false, false)).isTrue();
        assertThat(policy.canSee(leadOwnedBy(null), me, false, false)).isFalse();
        assertThat(policy.canSee(leadOwnedBy(other), me, false, false)).isFalse();
    }

    @Test
    void ownPlusPoolSeesOwnAndUnassignedButNotOthers() {
        assertThat(policy.canSee(leadOwnedBy(me), me, false, true)).isTrue();
        assertThat(policy.canSee(leadOwnedBy(null), me, false, true)).isTrue();
        assertThat(policy.canSee(leadOwnedBy(other), me, false, true)).isFalse();
    }

    @Test
    void seeAllSeesEveryLead() {
        assertThat(policy.canSee(leadOwnedBy(other), me, true, false)).isTrue();
        assertThat(policy.canSee(leadOwnedBy(null), me, true, false)).isTrue();
        assertThat(policy.canSee(leadOwnedBy(me), me, true, false)).isTrue();
    }
}
