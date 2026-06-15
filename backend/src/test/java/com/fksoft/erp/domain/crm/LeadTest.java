package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeadTest {

    private static final UUID CREATOR = UUID.randomUUID();

    private final Origin origin = Origin.create("WEBSITE", "Website", 1);
    private final InteractionType noteType = InteractionType.create("INTERNAL_NOTE", "Nota interna", 5);

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
    void qualifiesFromNewAndKeepsTheOutcome() {
        Lead lead = Lead.register(command("11999999999", null, null, null), origin, CREATOR);
        UUID byUser = UUID.randomUUID();

        lead.qualify(byUser, "bom perfil");

        assertThat(lead.status()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(lead.qualifiedBy()).isEqualTo(byUser);
        assertThat(lead.qualifiedAt()).isNotNull();
        assertThat(lead.qualificationNote()).isEqualTo("bom perfil");
    }

    @Test
    void cannotQualifyALostLead() {
        Lead lead = Lead.register(command("11999999999", null, null, null), origin, CREATOR);
        lead.markLost(LossReason.create("NO_RESPONSE", "Sem resposta", 1), CREATOR, null);

        assertThatThrownBy(() -> lead.qualify(CREATOR, null)).isInstanceOf(LeadCannotBeQualifiedException.class);
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
}
