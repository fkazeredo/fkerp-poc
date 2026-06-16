package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
    private LeadIndicatorQueries indicatorQueries;

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
    void listBuildsViewsWithMainContactResponsibleAndLatestInteraction() {
        UUID responsibleA = UUID.randomUUID();
        Origin origin = Origin.create("WEBSITE", "Website", 1);
        Lead withPhone = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), responsibleA, null),
                origin,
                UUID.randomUUID());
        Lead unassignedEmail = Lead.register(
                new RegisterLeadCommand("Joao", null, null, "joao@example.com", UUID.randomUUID(), null, null),
                origin,
                UUID.randomUUID());
        Pageable pageable = PageRequest.of(0, 20);

        when(accessPolicy.visibleTo(any(), anyBoolean(), anyBoolean())).thenReturn((root, query, cb) -> null);
        when(leads.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(withPhone, unassignedEmail), pageable, 2));
        LatestInteractionRow row = mock(LatestInteractionRow.class);
        when(row.getLeadId()).thenReturn(withPhone.id());
        when(row.getOccurredAt()).thenReturn(Instant.parse("2026-06-15T10:00:00Z"));
        when(row.getTypeLabel()).thenReturn("Ligação");
        when(leads.findLatestInteractions(anyList())).thenReturn(List.of(row));
        User responsible = mock(User.class);
        when(responsible.id()).thenReturn(responsibleA);
        when(responsible.username()).thenReturn("ana");
        when(users.findAllById(any())).thenReturn(List.of(responsible));

        Page<LeadListView> page = service.list(
                new LeadSearchCriteria(null, null, null, false, null, null, null),
                pageable,
                UUID.randomUUID(),
                false,
                true);

        LeadListView assigned = page.getContent().get(0);
        assertThat(assigned.name()).isEqualTo("Maria");
        assertThat(assigned.mainContact()).isEqualTo("11999999999");
        assertThat(assigned.responsibleName()).isEqualTo("ana");
        assertThat(assigned.unassigned()).isFalse();
        assertThat(assigned.lastInteractionType()).isEqualTo("Ligação");
        assertThat(assigned.lastInteractionAt()).isEqualTo(Instant.parse("2026-06-15T10:00:00Z"));

        LeadListView unassigned = page.getContent().get(1);
        assertThat(unassigned.mainContact()).isEqualTo("joao@example.com");
        assertThat(unassigned.responsibleName()).isNull();
        assertThat(unassigned.unassigned()).isTrue();
        assertThat(unassigned.lastInteractionAt()).isNull();
    }

    @Test
    void detailReturnsViewForAVisibleLead() {
        UUID id = UUID.randomUUID();
        Lead lead = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), null, null),
                Origin.create("WEBSITE", "Website", 1),
                UUID.randomUUID());
        when(leads.findById(id)).thenReturn(Optional.of(lead));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);

        LeadDetailView view = service.detail(id, UUID.randomUUID(), false, false);

        assertThat(view.name()).isEqualTo("Maria");
        assertThat(view.status()).isEqualTo(LeadStatus.NEW);
        assertThat(view.interactions()).isEmpty();
        assertThat(view.qualification()).isNull();
        assertThat(view.loss()).isNull();
    }

    @Test
    void detailThrowsNotFoundWhenAbsent() {
        UUID id = UUID.randomUUID();
        when(leads.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(id, UUID.randomUUID(), false, false))
                .isInstanceOf(LeadNotFoundException.class);
    }

    @Test
    void detailThrowsAccessDeniedWhenNotVisible() {
        UUID id = UUID.randomUUID();
        Lead lead = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), UUID.randomUUID(), null),
                Origin.create("WEBSITE", "Website", 1),
                UUID.randomUUID());
        when(leads.findById(id)).thenReturn(Optional.of(lead));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.detail(id, UUID.randomUUID(), false, false))
                .isInstanceOf(LeadAccessDeniedException.class);
    }

    @Test
    void reassignRejectsWhenAssignmentNotAllowed() {
        UUID id = UUID.randomUUID();
        Lead lead = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), null, null),
                Origin.create("WEBSITE", "Website", 1),
                UUID.randomUUID());
        when(leads.findById(id)).thenReturn(Optional.of(lead));
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
    void recordInteractionWithEffectiveContactReturnsContactedDetail() {
        UUID id = UUID.randomUUID();
        when(leads.findById(id)).thenReturn(Optional.of(visibleLead()));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);
        UUID typeId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        when(interactionTypes.findById(typeId))
                .thenReturn(Optional.of(InteractionType.create("PHONE_CALL", "Ligação", 1)));
        when(interactionResults.findById(resultId))
                .thenReturn(Optional.of(InteractionResult.create("CONTACT_MADE", "Contato realizado", 1)));
        when(leads.saveAndFlush(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));
        when(users.findAllById(any())).thenReturn(List.of());
        RecordInteractionCommand cmd =
                new RecordInteractionCommand(typeId, resultId, "Conversamos", Instant.now(), null);

        LeadDetailView view = service.recordInteraction(id, cmd, UUID.randomUUID(), false, false);

        assertThat(view.status()).isEqualTo(LeadStatus.CONTACTED);
        assertThat(view.interactions()).hasSize(1);
        assertThat(view.interactions().get(0).typeLabel()).isEqualTo("Ligação");
        assertThat(view.interactions().get(0).resultLabel()).isEqualTo("Contato realizado");
    }

    @Test
    void qualifyMapsTheMainInterestIntoTheDetail() {
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
        when(leads.saveAndFlush(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));
        when(users.findAllById(any())).thenReturn(List.of());

        LeadDetailView view = service.qualify(id, "Pacote corporativo", "bom perfil", responsible, false, false);

        assertThat(view.status()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(view.qualification().mainInterest()).isEqualTo("Pacote corporativo");
        assertThat(view.qualification().note()).isEqualTo("bom perfil");
    }

    @Test
    void indicatorsDeriveTotalFromStatusesAndResolveResponsibleNames() {
        UUID r1 = UUID.randomUUID();
        when(accessPolicy.visibleTo(any(), anyBoolean(), anyBoolean())).thenReturn((root, q, cb) -> null);
        when(indicatorQueries.countByStatus(any(), any(), any()))
                .thenReturn(Map.of(LeadStatus.NEW, 3L, LeadStatus.LOST, 2L));
        when(indicatorQueries.countByOrigin(any(), any(), any()))
                .thenReturn(List.of(new OriginCountView("Website", 4L)));
        when(indicatorQueries.countByResponsible(any(), any(), any()))
                .thenReturn(List.of(new ResponsibleCount(r1, 3L), new ResponsibleCount(null, 2L)));
        when(indicatorQueries.countWaitingFirstContact(any(), any(), any())).thenReturn(1L);
        User user = mock(User.class);
        when(user.id()).thenReturn(r1);
        when(user.username()).thenReturn("ana");
        when(users.findAllById(any())).thenReturn(List.of(user));

        LeadIndicatorsView view = service.indicators(UUID.randomUUID(), false, false, null, null);

        assertThat(view.total()).isEqualTo(5);
        assertThat(view.newLeads()).isEqualTo(3);
        assertThat(view.lost()).isEqualTo(2);
        assertThat(view.contacted()).isZero();
        assertThat(view.qualified()).isZero();
        assertThat(view.waitingFirstContact()).isEqualTo(1);
        assertThat(view.byOrigin()).containsExactly(new OriginCountView("Website", 4L));
        assertThat(view.byResponsible())
                .containsExactlyInAnyOrder(new ResponsibleCountView("ana", 3L), new ResponsibleCountView(null, 2L));
    }

    private static Lead visibleLead() {
        return Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), null, null),
                Origin.create("WEBSITE", "Website", 1),
                UUID.randomUUID());
    }
}
