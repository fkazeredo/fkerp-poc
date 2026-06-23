package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.erp.domain.crm.exception.LeadCannotBeMarkedLostException;
import com.fksoft.erp.domain.crm.exception.LeadCannotBeQualifiedException;
import com.fksoft.erp.domain.crm.exception.LeadContactRequiredException;
import com.fksoft.erp.domain.crm.exception.LeadQualificationRequiresResponsibleException;
import com.fksoft.erp.domain.crm.model.InteractionResult;
import com.fksoft.erp.domain.crm.model.InteractionType;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.LossReason;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.service.data.RegisterLeadCommand;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Lead aggregate. The lifecycle is a fixed enum state machine with pre-defined
 * transitions enforced on the entity: a contact moves NEW → CONTACTED; qualify requires CONTACTED and a
 * responsible; lose is allowed from any non-terminal state. Covers registration, the transitions and their
 * guards (happy + sad paths), the append-only interaction history and the assignment history.
 */
class LeadTest {

    private static final UUID CREATOR = UUID.randomUUID();

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
        return Lead.register(command(phone, whatsapp, email, responsible), origin, CREATOR);
    }

    @Test
    void startsInTheInitialState() {
        Lead lead = register("11999999999", null, null, null);
        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);
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
        lead.markContacted(byUser);

        lead.applyQualification("Pacote corporativo", "bom perfil", byUser);

        assertThat(lead.status()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(lead.qualifiedBy()).isEqualTo(byUser);
        assertThat(lead.qualifiedAt()).isNotNull();
        assertThat(lead.mainInterest()).isEqualTo("Pacote corporativo");
        assertThat(lead.qualificationNote()).isEqualTo("bom perfil");
    }

    @Test
    void qualifyingANewLeadIsRejected() {
        Lead lead = register("11999999999", null, null, UUID.randomUUID());
        assertThatThrownBy(() -> lead.applyQualification("Pacote", null, CREATOR))
                .isInstanceOf(LeadCannotBeQualifiedException.class);
    }

    @Test
    void qualifyingWithoutAResponsibleIsRejected() {
        Lead lead = register("11999999999", null, null, null);
        lead.markContacted(CREATOR);
        assertThatThrownBy(() -> lead.applyQualification("Pacote", null, CREATOR))
                .isInstanceOf(LeadQualificationRequiresResponsibleException.class);
    }

    @Test
    void applyLossSetsTheOutcomeAndState() {
        Lead lead = register("11999999999", null, null, null);
        LossReason reason = LossReason.create("NO_RESPONSE", "Sem resposta", 1);

        lead.applyLoss(reason, CREATOR, "sumiu");

        assertThat(lead.status()).isEqualTo(LeadStatus.LOST);
        assertThat(lead.lossReason()).isSameAs(reason);
        assertThat(lead.lostBy()).isEqualTo(CREATOR);
        assertThat(lead.lostAt()).isNotNull();
        assertThat(lead.lossNote()).isEqualTo("sumiu");
    }

    @Test
    void applyLossAcceptsAnOptionalNullNote() {
        Lead lead = register("11999999999", null, null, null);
        lead.applyLoss(LossReason.create("NO_RESPONSE", "Sem resposta", 1), CREATOR, null);
        assertThat(lead.lossNote()).isNull();
    }

    @Test
    void losingAnAlreadyLostLeadIsRejected() {
        Lead lead = register("11999999999", null, null, null);
        lead.applyLoss(LossReason.create("NO_RESPONSE", "Sem resposta", 1), CREATOR, null);
        assertThatThrownBy(() -> lead.applyLoss(LossReason.create("NO_RESPONSE", "Sem resposta", 1), CREATOR, null))
                .isInstanceOf(LeadCannotBeMarkedLostException.class);
    }

    @Test
    void markContactedMovesTheState() {
        Lead lead = register("11999999999", null, null, null);
        UUID author = UUID.randomUUID();

        lead.markContacted(author);

        assertThat(lead.status()).isEqualTo(LeadStatus.CONTACTED);
        assertThat(lead.updatedBy()).isEqualTo(author);
    }

    @Test
    void recordInteractionAppendsHistoryWithoutChangingTheState() {
        Lead lead = register("11999999999", null, null, null);

        lead.recordInteraction(callType, result("CONTACT_MADE"), "Conversamos", Instant.now(), null, CREATOR);

        // the state move (NEW -> CONTACTED) is the service's job; the entity only records the interaction
        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);
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
