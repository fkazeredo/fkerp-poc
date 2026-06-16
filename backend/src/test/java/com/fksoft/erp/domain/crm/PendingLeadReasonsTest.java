package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.erp.domain.crm.model.InteractionResult;
import com.fksoft.erp.domain.crm.model.InteractionType;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LossReason;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.model.PendingLeadReasons;
import com.fksoft.erp.domain.crm.model.PendingReason;
import com.fksoft.erp.domain.crm.service.data.RegisterLeadCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingLeadReasonsTest {

    private final Origin origin = Origin.create("WEBSITE", "Website", 1);
    private final InteractionType call = InteractionType.create("PHONE_CALL", "Ligação", 1);
    private final InteractionResult contactMade = InteractionResult.create("CONTACT_MADE", "Contato realizado", 1);
    private final Instant now = Instant.parse("2026-06-15T12:00:00Z");
    private final UUID creator = UUID.randomUUID();

    private Lead newLead(UUID responsible) {
        return Lead.register(
                new RegisterLeadCommand("X", "11999999999", null, null, UUID.randomUUID(), responsible, null),
                origin,
                creator);
    }

    @Test
    void unassignedNewWithoutInteractionHasBothReasons() {
        assertThat(PendingLeadReasons.of(newLead(null), now, false))
                .containsExactlyInAnyOrder(PendingReason.UNASSIGNED, PendingReason.NEW_WITHOUT_INTERACTION);
    }

    @Test
    void assignedNewWithoutInteractionIsPendingOnlyForThat() {
        assertThat(PendingLeadReasons.of(newLead(UUID.randomUUID()), now, false))
                .containsExactly(PendingReason.NEW_WITHOUT_INTERACTION);
    }

    @Test
    void assignedNewWithAnInteractionIsNotPending() {
        assertThat(PendingLeadReasons.of(newLead(UUID.randomUUID()), now, true)).isEmpty();
    }

    @Test
    void contactedWithoutAPlannedNextContactIsPending() {
        UUID resp = UUID.randomUUID();
        Lead lead = newLead(resp);
        lead.recordInteraction(call, contactMade, "x", now.minusSeconds(60), null, resp);
        assertThat(PendingLeadReasons.of(lead, now, true)).containsExactly(PendingReason.CONTACTED_WITHOUT_OUTCOME);
    }

    @Test
    void overdueNextContactIsPending() {
        UUID resp = UUID.randomUUID();
        Lead lead = newLead(resp);
        lead.recordInteraction(call, contactMade, "x", now.minusSeconds(60), now.minusSeconds(30), resp);
        assertThat(PendingLeadReasons.of(lead, now, true)).containsExactly(PendingReason.OVERDUE_NEXT_CONTACT);
    }

    @Test
    void contactedWithAFutureNextContactIsNotPending() {
        UUID resp = UUID.randomUUID();
        Lead lead = newLead(resp);
        lead.recordInteraction(call, contactMade, "x", now.minusSeconds(60), now.plusSeconds(3600), resp);
        assertThat(PendingLeadReasons.of(lead, now, true)).isEmpty();
    }

    @Test
    void qualifiedLeadIsNeverPending() {
        UUID resp = UUID.randomUUID();
        Lead lead = newLead(resp);
        lead.recordInteraction(call, contactMade, "x", now.minusSeconds(60), now.minusSeconds(30), resp);
        lead.qualify(resp, "Pacote", null);
        assertThat(PendingLeadReasons.of(lead, now, true)).isEmpty();
    }

    @Test
    void lostLeadIsNeverPending() {
        Lead lead = newLead(null); // unassigned, but...
        lead.markLost(LossReason.create("NO_RESPONSE", "Sem resposta", 1), creator, null);
        assertThat(PendingLeadReasons.of(lead, now, false)).isEmpty();
    }
}
