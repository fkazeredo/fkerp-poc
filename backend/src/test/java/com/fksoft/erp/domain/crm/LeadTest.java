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
}
