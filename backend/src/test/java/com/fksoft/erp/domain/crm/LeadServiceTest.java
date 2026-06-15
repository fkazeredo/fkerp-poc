package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.identity.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leads;

    @Mock
    private OriginRepository origins;

    @Mock
    private InteractionTypeRepository interactionTypes;

    @Mock
    private UserRepository users;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private LeadService service;

    @Test
    void registersLeadAsNewWithNoteAndPublishesEvent() {
        UUID createdBy = UUID.randomUUID();
        UUID originId = UUID.randomUUID();
        Origin origin = Origin.create("WEBSITE", "Website", 1);
        InteractionType noteType = InteractionType.create("INTERNAL_NOTE", "Nota interna", 5);
        when(origins.findById(originId)).thenReturn(Optional.of(origin));
        when(interactionTypes.findByCode("INTERNAL_NOTE")).thenReturn(Optional.of(noteType));
        RegisterLeadCommand command =
                new RegisterLeadCommand("Maria", "11999999999", null, null, originId, null, "primeira nota");

        UUID id = service.register(command, createdBy);

        assertThat(id).isNotNull();
        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leads).save(captor.capture());
        Lead saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(LeadStatus.NEW);
        assertThat(saved.interactions()).hasSize(1);
        verify(events).publishEvent(any(LeadRegistered.class));
    }

    @Test
    void rejectsUnknownOrigin() {
        UUID originId = UUID.randomUUID();
        when(origins.findById(originId)).thenReturn(Optional.empty());
        RegisterLeadCommand command = new RegisterLeadCommand("Maria", "11999999999", null, null, originId, null, null);

        assertThatThrownBy(() -> service.register(command, UUID.randomUUID()))
                .isInstanceOf(OriginNotAvailableException.class);
    }

    @Test
    void rejectsUnknownResponsible() {
        UUID originId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        when(origins.findById(originId)).thenReturn(Optional.of(Origin.create("WEBSITE", "Website", 1)));
        when(users.findById(responsibleId)).thenReturn(Optional.empty());
        RegisterLeadCommand command =
                new RegisterLeadCommand("Maria", "11999999999", null, null, originId, responsibleId, null);

        assertThatThrownBy(() -> service.register(command, UUID.randomUUID()))
                .isInstanceOf(ResponsiblePersonNotFoundException.class);
    }
}
