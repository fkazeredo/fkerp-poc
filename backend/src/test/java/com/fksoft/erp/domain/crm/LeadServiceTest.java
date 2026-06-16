package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.dto.RecordInteractionCommand;
import com.fksoft.erp.domain.crm.dto.RegisterLeadCommand;
import com.fksoft.erp.domain.crm.exception.DuplicateLeadException;
import com.fksoft.erp.domain.crm.exception.InteractionResultNotAvailableException;
import com.fksoft.erp.domain.crm.exception.InteractionTypeNotAvailableException;
import com.fksoft.erp.domain.crm.exception.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.LeadAssignmentNotAllowedException;
import com.fksoft.erp.domain.crm.exception.OriginNotAvailableException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.crm.model.InteractionResult;
import com.fksoft.erp.domain.crm.model.InteractionType;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadRegistered;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.repository.InteractionResultRepository;
import com.fksoft.erp.domain.crm.repository.InteractionTypeRepository;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.LossReasonRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.crm.service.LeadAccessPolicy;
import com.fksoft.erp.domain.crm.service.LeadAssignmentPolicy;
import com.fksoft.erp.domain.crm.service.LeadService;
import com.fksoft.erp.domain.identity.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests of the Lead command service (register + transitions). Reads moved to the read side
 * ({@code LeadReadServiceTest}); the transitions now return {@code void}, so these assert the mutated
 * aggregate's state and that it is saved.
 */
@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leads;

    @Mock
    private OriginRepository origins;

    @Mock
    private InteractionTypeRepository interactionTypes;

    @Mock
    private InteractionResultRepository interactionResults;

    @Mock
    private LossReasonRepository lossReasons;

    @Mock
    private UserRepository users;

    @Mock
    private LeadAccessPolicy accessPolicy;

    @Mock
    private LeadAssignmentPolicy assignmentPolicy;

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

    @Test
    void rejectsDuplicateOpenLeadAtRegistration() {
        UUID originId = UUID.randomUUID();
        Origin origin = Origin.create("WEBSITE", "Website", 1);
        Lead existing = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, originId, null, null),
                origin,
                UUID.randomUUID());
        when(origins.findById(originId)).thenReturn(Optional.of(origin));
        when(leads.findOpenDuplicates(any(), any(), any())).thenReturn(List.of(existing));
        RegisterLeadCommand command =
                new RegisterLeadCommand("Maria 2", "11999999999", null, null, originId, null, null);

        assertThatThrownBy(() -> service.register(command, UUID.randomUUID()))
                .isInstanceOf(DuplicateLeadException.class);
        verify(leads, never()).save(any());
    }

    @Test
    void reassignRejectsWhenAssignmentNotAllowed() {
        UUID id = UUID.randomUUID();
        when(leads.findById(id)).thenReturn(Optional.of(visibleLead()));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);
        when(assignmentPolicy.canAssign(any(), any(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.reassign(id, UUID.randomUUID(), UUID.randomUUID(), false, false, false))
                .isInstanceOf(LeadAssignmentNotAllowedException.class);
    }

    @Test
    void recordInteractionRejectsAnUnknownOrInactiveType() {
        UUID id = UUID.randomUUID();
        when(leads.findById(id)).thenReturn(Optional.of(visibleLead()));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);
        UUID typeId = UUID.randomUUID();
        when(interactionTypes.findById(typeId)).thenReturn(Optional.empty());
        RecordInteractionCommand cmd =
                new RecordInteractionCommand(typeId, UUID.randomUUID(), "desc", Instant.now(), null);

        assertThatThrownBy(() -> service.recordInteraction(id, cmd, UUID.randomUUID(), false, false))
                .isInstanceOf(InteractionTypeNotAvailableException.class);
    }

    @Test
    void recordInteractionRejectsAnUnknownOrInactiveResult() {
        UUID id = UUID.randomUUID();
        when(leads.findById(id)).thenReturn(Optional.of(visibleLead()));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);
        UUID typeId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        when(interactionTypes.findById(typeId))
                .thenReturn(Optional.of(InteractionType.create("PHONE_CALL", "Ligação", 1)));
        when(interactionResults.findById(resultId)).thenReturn(Optional.empty());
        RecordInteractionCommand cmd = new RecordInteractionCommand(typeId, resultId, "desc", Instant.now(), null);

        assertThatThrownBy(() -> service.recordInteraction(id, cmd, UUID.randomUUID(), false, false))
                .isInstanceOf(InteractionResultNotAvailableException.class);
    }

    @Test
    void recordInteractionDeniedWhenLeadNotVisible() {
        UUID id = UUID.randomUUID();
        when(leads.findById(id)).thenReturn(Optional.of(visibleLead()));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(false);
        RecordInteractionCommand cmd =
                new RecordInteractionCommand(UUID.randomUUID(), UUID.randomUUID(), "desc", Instant.now(), null);

        assertThatThrownBy(() -> service.recordInteraction(id, cmd, UUID.randomUUID(), false, false))
                .isInstanceOf(LeadAccessDeniedException.class);
    }

    @Test
    void recordInteractionWithEffectiveContactMovesLeadToContacted() {
        UUID id = UUID.randomUUID();
        Lead lead = visibleLead();
        when(leads.findById(id)).thenReturn(Optional.of(lead));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);
        UUID typeId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        when(interactionTypes.findById(typeId))
                .thenReturn(Optional.of(InteractionType.create("PHONE_CALL", "Ligação", 1)));
        when(interactionResults.findById(resultId))
                .thenReturn(Optional.of(InteractionResult.create("CONTACT_MADE", "Contato realizado", 1)));
        RecordInteractionCommand cmd =
                new RecordInteractionCommand(typeId, resultId, "Conversamos", Instant.now(), null);

        service.recordInteraction(id, cmd, UUID.randomUUID(), false, false);

        assertThat(lead.status()).isEqualTo(LeadStatus.CONTACTED);
        assertThat(lead.interactions()).hasSize(1);
        verify(leads).saveAndFlush(lead);
    }

    @Test
    void qualifyMovesLeadToQualifiedWithInterest() {
        UUID id = UUID.randomUUID();
        UUID responsible = UUID.randomUUID();
        Lead lead = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), responsible, null),
                Origin.create("WEBSITE", "Website", 1),
                UUID.randomUUID());
        lead.recordInteraction(
                InteractionType.create("PHONE_CALL", "Ligação", 1),
                InteractionResult.create("CONTACT_MADE", "Contato realizado", 1),
                "falamos",
                Instant.now(),
                null,
                responsible);
        when(leads.findById(id)).thenReturn(Optional.of(lead));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);

        service.qualify(id, "Pacote corporativo", "bom perfil", responsible, false, false);

        assertThat(lead.status()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(lead.mainInterest()).isEqualTo("Pacote corporativo");
        assertThat(lead.qualificationNote()).isEqualTo("bom perfil");
        verify(leads).saveAndFlush(lead);
    }

    private static Lead visibleLead() {
        return Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), null, null),
                Origin.create("WEBSITE", "Website", 1),
                UUID.randomUUID());
    }
}
