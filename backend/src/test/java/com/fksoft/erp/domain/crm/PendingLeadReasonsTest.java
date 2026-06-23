package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.erp.domain.crm.model.InteractionResult;
import com.fksoft.erp.domain.crm.model.InteractionType;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LossReason;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.model.PendingLeadReasons;
import com.fksoft.erp.domain.crm.service.data.RegisterLeadCommand;
import com.fksoft.erp.domain.workflow.WorkflowAttentionRule;
import com.fksoft.erp.domain.workflow.WorkflowDefinition;
import com.fksoft.erp.domain.workflow.WorkflowState;
import com.fksoft.erp.domain.workflow.WorkflowStateCategory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingLeadReasonsTest {

    private final Origin origin = Origin.create("WEBSITE", "Website", 1);
    private final InteractionType call = InteractionType.create("PHONE_CALL", "Ligação", 1);
    private final InteractionResult contactMade = InteractionResult.create("CONTACT_MADE", "Contato realizado", 1);
    private final Instant now = Instant.parse("2026-06-15T12:00:00Z");
    private final UUID creator = UUID.randomUUID();
    private final WorkflowDefinition wf = WorkflowDefinition.of("lead", "Lead");
    private final WorkflowState newState = WorkflowState.of(wf, "NEW", "Novo", WorkflowStateCategory.INITIAL, 1);
    private final WorkflowState contacted =
            WorkflowState.of(wf, "CONTACTED", "Contatado", WorkflowStateCategory.ACTIVE, 2);
    private final WorkflowState qualified =
            WorkflowState.of(wf, "QUALIFIED", "Qualificado", WorkflowStateCategory.ACTIVE, 3);
    private final WorkflowState lost =
            WorkflowState.of(wf, "LOST", "Perdido", WorkflowStateCategory.TERMINAL_NEGATIVE, 4);

    private final List<WorkflowAttentionRule> rules = List.of(
            WorkflowAttentionRule.of(wf, "UNASSIGNED", null, null, "UNASSIGNED", "Sem responsável", 1),
            WorkflowAttentionRule.of(wf, "NEW_WITHOUT_INTERACTION", null, null, "NEW_WITHOUT_INTERACTION", "Novo", 2),
            WorkflowAttentionRule.of(wf, "OVERDUE_NEXT_CONTACT", null, null, "OVERDUE_NEXT_CONTACT", "Vencido", 3),
            WorkflowAttentionRule.of(
                    wf, "CONTACTED_WITHOUT_OUTCOME", null, null, "CONTACTED_WITHOUT_OUTCOME", "Sem desfecho", 4));

    private Lead newLead(UUID responsible) {
        return Lead.register(
                new RegisterLeadCommand("X", "11999999999", null, null, UUID.randomUUID(), responsible, null),
                origin,
                newState,
                creator);
    }

    @Test
    void unassignedNewWithoutInteractionHasBothReasons() {
        assertThat(PendingLeadReasons.of(newLead(null), now, false, rules))
                .containsExactlyInAnyOrder("UNASSIGNED", "NEW_WITHOUT_INTERACTION");
    }

    @Test
    void assignedNewWithoutInteractionIsPendingOnlyForThat() {
        assertThat(PendingLeadReasons.of(newLead(UUID.randomUUID()), now, false, rules))
                .containsExactly("NEW_WITHOUT_INTERACTION");
    }

    @Test
    void assignedNewWithAnInteractionIsNotPending() {
        assertThat(PendingLeadReasons.of(newLead(UUID.randomUUID()), now, true, rules))
                .isEmpty();
    }

    @Test
    void contactedWithoutAPlannedNextContactIsPending() {
        UUID resp = UUID.randomUUID();
        Lead lead = newLead(resp);
        lead.recordInteraction(call, contactMade, "x", now.minusSeconds(60), null, resp);
        lead.markContacted(contacted, resp); // the engine performs this NEW -> CONTACTED move in the service
        assertThat(PendingLeadReasons.of(lead, now, true, rules)).containsExactly("CONTACTED_WITHOUT_OUTCOME");
    }

    @Test
    void overdueNextContactIsPending() {
        UUID resp = UUID.randomUUID();
        Lead lead = newLead(resp);
        lead.recordInteraction(call, contactMade, "x", now.minusSeconds(60), now.minusSeconds(30), resp);
        assertThat(PendingLeadReasons.of(lead, now, true, rules)).containsExactly("OVERDUE_NEXT_CONTACT");
    }

    @Test
    void contactedWithAFutureNextContactIsNotPending() {
        UUID resp = UUID.randomUUID();
        Lead lead = newLead(resp);
        lead.recordInteraction(call, contactMade, "x", now.minusSeconds(60), now.plusSeconds(3600), resp);
        assertThat(PendingLeadReasons.of(lead, now, true, rules)).isEmpty();
    }

    @Test
    void qualifiedLeadIsNeverPending() {
        UUID resp = UUID.randomUUID();
        Lead lead = newLead(resp);
        lead.recordInteraction(call, contactMade, "x", now.minusSeconds(60), now.minusSeconds(30), resp);
        lead.applyQualification(qualified, "Pacote", null, resp);
        assertThat(PendingLeadReasons.of(lead, now, true, rules)).isEmpty();
    }

    @Test
    void lostLeadIsNeverPending() {
        Lead lead = newLead(null); // unassigned, but...
        lead.applyLoss(lost, LossReason.create("NO_RESPONSE", "Sem resposta", 1), creator, null);
        assertThat(PendingLeadReasons.of(lead, now, false, rules)).isEmpty();
    }
}
