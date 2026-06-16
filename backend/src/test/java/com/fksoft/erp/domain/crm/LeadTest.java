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

    @Test
    void startsAsNew() {
        Lead lead = Lead.register(command("11999999999", null, null, null), origin, CREATOR);
        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);
        assertThat(lead.origin()).isSameAs(origin);
        assertThat(lead.createdBy()).isEqualTo(CREATOR);
    }

    @Test
    void requiresAtLeastOneContact() {
        assertThatThrownBy(() -> Lead.register(command(null, null, null, null), origin, CREATOR))
                .isInstanceOf(LeadContactRequiredException.class);
    }

    @Test
    void allowsLeadWithoutResponsible() {
        Lead lead = Lead.register(command(null, null, "maria@example.com", null), origin, CREATOR);
        assertThat(lead.hasResponsible()).isFalse();
        assertThat(lead.responsiblePersonId()).isNull();
    }

    @Test
    void initialNoteBecomesFirstInteraction() {
        Lead lead = Lead.register(command("11999999999", null, null, null), origin, CREATOR);
        lead.addInitialNote(noteType, "Cliente pediu retorno", CREATOR);
        assertThat(lead.interactions()).hasSize(1);
        assertThat(lead.interactions().get(0).type()).isSameAs(noteType);
        assertThat(lead.interactions().get(0).content()).isEqualTo("Cliente pediu retorno");
    }

    @Test
    void qualifiesAContactedAssignedLeadAndKeepsTheOutcome() {
        UUID responsible = UUID.randomUUID();
        Lead lead = Lead.register(command("11999999999", null, null, responsible), origin, CREATOR);
        lead.recordInteraction(callType, result("CONTACT_MADE"), "falamos", Instant.now(), null, CREATOR);
        UUID byUser = UUID.randomUUID();

        lead.qualify(byUser, "Pacote corporativo", "bom perfil");

        assertThat(lead.status()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(lead.qualifiedBy()).isEqualTo(byUser);
        assertThat(lead.qualifiedAt()).isNotNull();
        assertThat(lead.mainInterest()).isEqualTo("Pacote corporativo");
        assertThat(lead.qualificationNote()).isEqualTo("bom perfil");
    }

    @Test
    void cannotQualifyANewLead() {
        UUID responsible = UUID.randomUUID();
        Lead lead = Lead.register(command("11999999999", null, null, responsible), origin, CREATOR);

        assertThatThrownBy(() -> lead.qualify(CREATOR, "Pacote", null))
                .isInstanceOf(LeadCannotBeQualifiedException.class);
    }

    @Test
    void cannotQualifyALeadWithoutResponsible() {
        Lead lead = Lead.register(command("11999999999", null, null, null), origin, CREATOR);
        lead.recordInteraction(callType, result("CONTACT_MADE"), "falamos", Instant.now(), null, CREATOR);

        assertThatThrownBy(() -> lead.qualify(CREATOR, "Pacote", null))
                .isInstanceOf(LeadQualificationRequiresResponsibleException.class);
    }

    @Test
    void cannotQualifyALostLead() {
        UUID responsible = UUID.randomUUID();
        Lead lead = Lead.register(command("11999999999", null, null, responsible), origin, CREATOR);
        lead.markLost(LossReason.create("NO_RESPONSE", "Sem resposta", 1), CREATOR, null);

        assertThatThrownBy(() -> lead.qualify(CREATOR, "Pacote", null))
                .isInstanceOf(LeadCannotBeQualifiedException.class);
    }

    @Test
    void marksLostWithReasonAndKeepsTheOutcome() {
        Lead lead = Lead.register(command("11999999999", null, null, null), origin, CREATOR);
        LossReason reason = LossReason.create("NO_RESPONSE", "Sem resposta", 1);

        lead.markLost(reason, CREATOR, "sumiu");

        assertThat(lead.status()).isEqualTo(LeadStatus.LOST);
        assertThat(lead.lossReason()).isSameAs(reason);
        assertThat(lead.lostAt()).isNotNull();
        assertThat(lead.lossNote()).isEqualTo("sumiu");
    }

    @Test
    void marksAContactedLeadLostWithoutANote() {
        UUID responsible = UUID.randomUUID();
        Lead lead = Lead.register(command("11999999999", null, null, responsible), origin, CREATOR);
        lead.recordInteraction(callType, result("CONTACT_MADE"), "falamos", Instant.now(), null, CREATOR);
        LossReason reason = LossReason.create("NO_RESPONSE", "Sem resposta", 1);

        lead.markLost(reason, CREATOR, null);

        assertThat(lead.status()).isEqualTo(LeadStatus.LOST);
        assertThat(lead.lossReason()).isSameAs(reason);
        assertThat(lead.lostBy()).isEqualTo(CREATOR);
        assertThat(lead.lostAt()).isNotNull();
        assertThat(lead.lossNote()).isNull(); // note is optional
    }

    @Test
    void marksAQualifiedLeadLost() {
        UUID responsible = UUID.randomUUID();
        Lead lead = Lead.register(command("11999999999", null, null, responsible), origin, CREATOR);
        lead.recordInteraction(callType, result("CONTACT_MADE"), "falamos", Instant.now(), null, CREATOR);
        lead.qualify(CREATOR, "Pacote", null);

        lead.markLost(LossReason.create("BOUGHT_ELSEWHERE", "Comprou em outro lugar", 5), CREATOR, null);

        assertThat(lead.status()).isEqualTo(LeadStatus.LOST);
    }

    @Test
    void cannotMarkAnAlreadyLostLeadLostAgain() {
        Lead lead = Lead.register(command("11999999999", null, null, null), origin, CREATOR);
        LossReason reason = LossReason.create("NO_RESPONSE", "Sem resposta", 1);
        lead.markLost(reason, CREATOR, null);

        assertThatThrownBy(() -> lead.markLost(reason, CREATOR, null))
                .isInstanceOf(LeadCannotBeMarkedLostException.class);
    }

    @Test
    void recordsAssignmentHistoryAtCreationAndOnReassign() {
        UUID first = UUID.randomUUID();
        Lead lead = Lead.register(command("11999999999", null, null, first), origin, CREATOR);
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
        Lead lead = Lead.register(command("11999999999", null, null, responsible), origin, CREATOR);

        lead.reassign(responsible, CREATOR);

        assertThat(lead.assignments()).hasSize(1);
    }

    @Test
    void effectiveContactMovesANewLeadToContacted() {
        Lead lead = Lead.register(command("11999999999", null, null, null), origin, CREATOR);
        UUID author = UUID.randomUUID();

        lead.recordInteraction(callType, result("CONTACT_MADE"), "Conversamos", Instant.now(), null, author);

        assertThat(lead.status()).isEqualTo(LeadStatus.CONTACTED);
        assertThat(lead.interactions()).hasSize(1);
        assertThat(lead.interactions().get(0).result().code()).isEqualTo("CONTACT_MADE");
        assertThat(lead.updatedBy()).isEqualTo(author);
    }

    @Test
    void aFailedAttemptKeepsTheLeadNewButKeepsTheHistory() {
        Lead lead = Lead.register(command("11999999999", null, null, null), origin, CREATOR);

        lead.recordInteraction(callType, result("NO_ANSWER"), "Não atendeu", Instant.now(), null, CREATOR);
        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);

        lead.recordInteraction(callType, result("INVALID_CONTACT"), "Número errado", Instant.now(), null, CREATOR);
        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);
        assertThat(lead.interactions()).hasSize(2);
    }

    @Test
    void needsFollowUpAndOtherAlsoCountAsEffectiveContact() {
        Lead a = Lead.register(command("11999999999", null, null, null), origin, CREATOR);
        a.recordInteraction(callType, result("NEEDS_FOLLOW_UP"), "Retornar", Instant.now(), null, CREATOR);
        assertThat(a.status()).isEqualTo(LeadStatus.CONTACTED);

        Lead b = Lead.register(command("11999999999", null, null, null), origin, CREATOR);
        b.recordInteraction(callType, result("OTHER"), "Outro", Instant.now(), null, CREATOR);
        assertThat(b.status()).isEqualTo(LeadStatus.CONTACTED);
    }

    @Test
    void recordingAnInteractionSchedulesTheNextContact() {
        Lead lead = Lead.register(command("11999999999", null, null, null), origin, CREATOR);
        Instant next = Instant.now().plus(Duration.ofDays(2));

        lead.recordInteraction(callType, result("INTERESTED"), "Quer proposta", Instant.now(), next, CREATOR);

        assertThat(lead.nextContactAt()).isEqualTo(next);
        assertThat(lead.interactions().get(0).nextContactAt()).isEqualTo(next);
    }

    @Test
    void recordingAnInteractionNeverRevertsAQualifiedLead() {
        UUID responsible = UUID.randomUUID();
        Lead lead = Lead.register(command("11999999999", null, null, responsible), origin, CREATOR);
        lead.recordInteraction(callType, result("CONTACT_MADE"), "falamos", Instant.now(), null, CREATOR);
        lead.qualify(CREATOR, "Pacote", null);

        lead.recordInteraction(callType, result("CONTACT_MADE"), "follow-up", Instant.now(), null, CREATOR);

        assertThat(lead.status()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(lead.interactions()).hasSize(2);
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
