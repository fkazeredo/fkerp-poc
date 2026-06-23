package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.erp.domain.crm.exception.LeadContactRequiredException;
import com.fksoft.erp.domain.crm.model.InteractionResult;
import com.fksoft.erp.domain.crm.model.InteractionType;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LossReason;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.service.data.RegisterLeadCommand;
import com.fksoft.erp.domain.workflow.WorkflowDefinition;
import com.fksoft.erp.domain.workflow.WorkflowState;
import com.fksoft.erp.domain.workflow.WorkflowStateCategory;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Lead aggregate. Since the workflow reform, the lifecycle transitions are validated by
 * the workflow engine in the application service; the entity exposes field-setting methods that move it to a
 * (pre-validated) target state. This test covers that entity surface — registration, the qualification/loss
 * outcome, the system contact move, the append-only interaction history (which no longer changes the state)
 * and assignment history. The transition <em>guards</em> (e.g. qualify requires CONTACTED + a responsible,
 * lose is not allowed from LOST) are covered end-to-end by the API integration tests.
 */
class LeadTest {

    private static final UUID CREATOR = UUID.randomUUID();

    private final WorkflowDefinition workflow = WorkflowDefinition.of("lead", "Lead");
    private final WorkflowState newState = WorkflowState.of(workflow, "NEW", "Novo", WorkflowStateCategory.INITIAL, 1);
    private final WorkflowState contacted =
            WorkflowState.of(workflow, "CONTACTED", "Contatado", WorkflowStateCategory.ACTIVE, 2);
    private final WorkflowState qualified =
            WorkflowState.of(workflow, "QUALIFIED", "Qualificado", WorkflowStateCategory.ACTIVE, 3);
    private final WorkflowState lost =
            WorkflowState.of(workflow, "LOST", "Perdido", WorkflowStateCategory.TERMINAL_NEGATIVE, 4);

    private final Origin origin = Origin.create("WEBSITE", "Website", 1);
    private final InteractionType noteType = InteractionType.create("INTERNAL_NOTE", "Nota interna", 5);
    private final InteractionType callType = InteractionType.create("PHONE_CALL", "Ligação", 1);

    private static InteractionResult result(String code) {
        return InteractionResult.create(code, code, 1);
    }

    private RegisterLeadCommand command(String phone, String whatsapp, String email, UUID responsible) {
        return new RegisterLeadCommand("Maria Silva", phone, whatsapp, email, UUID.randomUUID(), responsible, null);
    }

    private Lead register(String phone, String whatsapp, String email, UUID responsible) {
        return Lead.register(command(phone, whatsapp, email, responsible), origin, newState, CREATOR);
    }

    @Test
    void startsInTheInitialState() {
        Lead lead = register("11999999999", null, null, null);
        assertThat(lead.status()).isEqualTo("NEW");
        assertThat(lead.currentState()).isSameAs(newState);
        assertThat(lead.origin()).isSameAs(origin);
        assertThat(lead.createdBy()).isEqualTo(CREATOR);
    }

    @Test
    void requiresAtLeastOneContact() {
        assertThatThrownBy(() -> register(null, null, null, null)).isInstanceOf(LeadContactRequiredException.class);
    }

    @Test
    void allowsLeadWithoutResponsible() {
        Lead lead = register(null, null, "maria@example.com", null);
        assertThat(lead.hasResponsible()).isFalse();
        assertThat(lead.responsiblePersonId()).isNull();
    }

    @Test
    void initialNoteBecomesFirstInteraction() {
        Lead lead = register("11999999999", null, null, null);
        lead.addInitialNote(noteType, "Cliente pediu retorno", CREATOR);
        assertThat(lead.interactions()).hasSize(1);
        assertThat(lead.interactions().get(0).type()).isSameAs(noteType);
        assertThat(lead.interactions().get(0).content()).isEqualTo("Cliente pediu retorno");
    }

    @Test
    void applyQualificationSetsTheOutcomeAndState() {
        Lead lead = register("11999999999", null, null, UUID.randomUUID());
        UUID byUser = UUID.randomUUID();

        lead.applyQualification(qualified, "Pacote corporativo", "bom perfil", byUser);

        assertThat(lead.status()).isEqualTo("QUALIFIED");
        assertThat(lead.currentState()).isSameAs(qualified);
        assertThat(lead.qualifiedBy()).isEqualTo(byUser);
        assertThat(lead.qualifiedAt()).isNotNull();
        assertThat(lead.mainInterest()).isEqualTo("Pacote corporativo");
        assertThat(lead.qualificationNote()).isEqualTo("bom perfil");
    }

    @Test
    void applyLossSetsTheOutcomeAndState() {
        Lead lead = register("11999999999", null, null, null);
        LossReason reason = LossReason.create("NO_RESPONSE", "Sem resposta", 1);

        lead.applyLoss(lost, reason, CREATOR, "sumiu");

        assertThat(lead.status()).isEqualTo("LOST");
        assertThat(lead.currentState()).isSameAs(lost);
        assertThat(lead.lossReason()).isSameAs(reason);
        assertThat(lead.lostBy()).isEqualTo(CREATOR);
        assertThat(lead.lostAt()).isNotNull();
        assertThat(lead.lossNote()).isEqualTo("sumiu");
    }

    @Test
    void applyLossAcceptsAnOptionalNullNote() {
        Lead lead = register("11999999999", null, null, null);
        lead.applyLoss(lost, LossReason.create("NO_RESPONSE", "Sem resposta", 1), CREATOR, null);
        assertThat(lead.lossNote()).isNull();
    }

    @Test
    void markContactedMovesTheState() {
        Lead lead = register("11999999999", null, null, null);
        UUID author = UUID.randomUUID();

        lead.markContacted(contacted, author);

        assertThat(lead.status()).isEqualTo("CONTACTED");
        assertThat(lead.currentState()).isSameAs(contacted);
        assertThat(lead.updatedBy()).isEqualTo(author);
    }

    @Test
    void recordInteractionAppendsHistoryWithoutChangingTheState() {
        Lead lead = register("11999999999", null, null, null);

        lead.recordInteraction(callType, result("CONTACT_MADE"), "Conversamos", Instant.now(), null, CREATOR);

        // the state move (NEW -> CONTACTED) is the service's job via the engine; the entity only records
        assertThat(lead.status()).isEqualTo("NEW");
        assertThat(lead.interactions()).hasSize(1);
        assertThat(lead.interactions().get(0).result().code()).isEqualTo("CONTACT_MADE");
        assertThat(lead.updatedBy()).isEqualTo(CREATOR);
    }

    @Test
    void recordingAnInteractionSchedulesTheNextContact() {
        Lead lead = register("11999999999", null, null, null);
        Instant next = Instant.now().plus(Duration.ofDays(2));

        lead.recordInteraction(callType, result("INTERESTED"), "Quer proposta", Instant.now(), next, CREATOR);

        assertThat(lead.nextContactAt()).isEqualTo(next);
        assertThat(lead.interactions().get(0).nextContactAt()).isEqualTo(next);
    }

    @Test
    void recordsAssignmentHistoryAtCreationAndOnReassign() {
        UUID first = UUID.randomUUID();
        Lead lead = register("11999999999", null, null, first);
        assertThat(lead.assignments()).hasSize(1);
        assertThat(lead.assignments().get(0).fromResponsibleId()).isNull();
        assertThat(lead.assignments().get(0).toResponsibleId()).isEqualTo(first);

        UUID second = UUID.randomUUID();
        lead.reassign(second, CREATOR);

        assertThat(lead.responsiblePersonId()).isEqualTo(second);
        assertThat(lead.assignments()).hasSize(2);
        assertThat(lead.assignments().get(1).fromResponsibleId()).isEqualTo(first);
        assertThat(lead.assignments().get(1).toResponsibleId()).isEqualTo(second);
    }

    @Test
    void reassignToTheSameResponsibleIsANoOp() {
        UUID responsible = UUID.randomUUID();
        Lead lead = register("11999999999", null, null, responsible);

        lead.reassign(responsible, CREATOR);

        assertThat(lead.assignments()).hasSize(1);
    }

    @Test
    void effectiveContactClassifiesEveryResultExceptNoAnswerAndInvalidContact() {
        assertThat(result("CONTACT_MADE").isEffectiveContact()).isTrue();
        assertThat(result("ASKED_FOR_RETURN").isEffectiveContact()).isTrue();
        assertThat(result("INTERESTED").isEffectiveContact()).isTrue();
        assertThat(result("NOT_INTERESTED").isEffectiveContact()).isTrue();
        assertThat(result("NEEDS_FOLLOW_UP").isEffectiveContact()).isTrue();
        assertThat(result("OTHER").isEffectiveContact()).isTrue();
        assertThat(result("NO_ANSWER").isEffectiveContact()).isFalse();
        assertThat(result("INVALID_CONTACT").isEffectiveContact()).isFalse();
    }
}
