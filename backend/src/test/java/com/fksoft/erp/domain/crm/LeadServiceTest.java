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

import com.fksoft.erp.domain.crm.exception.DuplicateLeadException;
import com.fksoft.erp.domain.crm.exception.InteractionResultNotAvailableException;
import com.fksoft.erp.domain.crm.exception.InteractionTypeNotAvailableException;
import com.fksoft.erp.domain.crm.exception.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.LeadAssignmentNotAllowedException;
import com.fksoft.erp.domain.crm.exception.LeadNotFoundException;
import com.fksoft.erp.domain.crm.exception.OriginNotAvailableException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.crm.model.InteractionResult;
import com.fksoft.erp.domain.crm.model.InteractionType;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadRegistered;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.repository.InteractionResultRepository;
import com.fksoft.erp.domain.crm.repository.InteractionTypeRepository;
import com.fksoft.erp.domain.crm.repository.LatestInteractionRow;
import com.fksoft.erp.domain.crm.repository.LeadIndicatorQueries;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.LossReasonRepository;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import com.fksoft.erp.domain.crm.service.LeadAccessPolicy;
import com.fksoft.erp.domain.crm.service.LeadAssignmentPolicy;
import com.fksoft.erp.domain.crm.service.LeadService;
import com.fksoft.erp.domain.crm.service.data.LeadDetail;
import com.fksoft.erp.domain.crm.service.data.LeadIndicators;
import com.fksoft.erp.domain.crm.service.data.LeadListItem;
import com.fksoft.erp.domain.crm.service.data.LeadSearchCriteria;
import com.fksoft.erp.domain.crm.service.data.RecordInteractionCommand;
import com.fksoft.erp.domain.crm.service.data.RegisterLeadCommand;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.workflow.WorkflowDefinition;
import com.fksoft.erp.domain.workflow.WorkflowEngine;
import com.fksoft.erp.domain.workflow.WorkflowState;
import com.fksoft.erp.domain.workflow.WorkflowStateCategory;
import com.fksoft.erp.domain.workflow.WorkflowStateRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
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

/**
 * Unit tests of the Lead Application Service: one service per area handles both commands (register +
 * transitions) and reads (list / detail / indicators), assembling the {@code service.data} read models
 * from the entities and resolving names from Identity.
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
    private LeadIndicatorQueries indicatorQueries;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private WorkflowEngine workflow;

    @Mock
    private WorkflowStateRepository workflowStates;

    @InjectMocks
    private LeadService service;

    private static final WorkflowDefinition WF = WorkflowDefinition.of("lead", "Lead");
    private static final WorkflowState NEW_STATE =
            WorkflowState.of(WF, "NEW", "Novo", WorkflowStateCategory.INITIAL, 1);
    private static final WorkflowState CONTACTED_STATE =
            WorkflowState.of(WF, "CONTACTED", "Contatado", WorkflowStateCategory.ACTIVE, 2);
    private static final WorkflowState QUALIFIED_STATE =
            WorkflowState.of(WF, "QUALIFIED", "Qualificado", WorkflowStateCategory.ACTIVE, 3);

    @Test
    void registersLeadAsNewWithNoteAndPublishesEvent() {
        UUID createdBy = UUID.randomUUID();
        UUID originId = UUID.randomUUID();
        Origin origin = Origin.create("WEBSITE", "Website", 1);
        InteractionType noteType = InteractionType.create("INTERNAL_NOTE", "Nota interna", 5);
        when(origins.findById(originId)).thenReturn(Optional.of(origin));
        when(interactionTypes.findByCode("INTERNAL_NOTE")).thenReturn(Optional.of(noteType));
        when(workflowStates.findByDefinition_CodeAndCode("lead", "NEW")).thenReturn(Optional.of(NEW_STATE));
        RegisterLeadCommand command =
                new RegisterLeadCommand("Maria", "11999999999", null, null, originId, null, "primeira nota");

        UUID id = service.register(command, createdBy);

        assertThat(id).isNotNull();
        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leads).save(captor.capture());
        Lead saved = captor.getValue();
        assertThat(saved.status()).isEqualTo("NEW");
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
                NEW_STATE,
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
    void listBuildsItemsWithMainContactResponsibleAndLatestInteraction() {
        UUID responsibleA = UUID.randomUUID();
        Origin origin = Origin.create("WEBSITE", "Website", 1);
        Lead withPhone = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), responsibleA, null),
                origin,
                NEW_STATE,
                UUID.randomUUID());
        Lead unassignedEmail = Lead.register(
                new RegisterLeadCommand("Joao", null, null, "joao@example.com", UUID.randomUUID(), null, null),
                origin,
                NEW_STATE,
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

        Page<LeadListItem> page = service.list(
                new LeadSearchCriteria(null, null, null, false, null, null, null),
                pageable,
                UUID.randomUUID(),
                false,
                true);

        LeadListItem assigned = page.getContent().get(0);
        assertThat(assigned.name()).isEqualTo("Maria");
        assertThat(assigned.mainContact()).isEqualTo("11999999999");
        assertThat(assigned.responsibleName()).isEqualTo("ana");
        assertThat(assigned.unassigned()).isFalse();
        assertThat(assigned.lastInteractionType()).isEqualTo("Ligação");
        assertThat(assigned.lastInteractionAt()).isEqualTo(Instant.parse("2026-06-15T10:00:00Z"));

        LeadListItem unassigned = page.getContent().get(1);
        assertThat(unassigned.mainContact()).isEqualTo("joao@example.com");
        assertThat(unassigned.responsibleName()).isNull();
        assertThat(unassigned.unassigned()).isTrue();
        assertThat(unassigned.lastInteractionAt()).isNull();
    }

    @Test
    void detailReturnsTheVisibleLead() {
        UUID id = UUID.randomUUID();
        Lead lead = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), null, null),
                Origin.create("WEBSITE", "Website", 1),
                NEW_STATE,
                UUID.randomUUID());
        when(leads.findById(id)).thenReturn(Optional.of(lead));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);

        LeadDetail detail = service.detail(id, UUID.randomUUID(), false, false);

        assertThat(detail.name()).isEqualTo("Maria");
        assertThat(detail.status()).isEqualTo("NEW");
        assertThat(detail.interactions()).isEmpty();
        assertThat(detail.qualification()).isNull();
        assertThat(detail.loss()).isNull();
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
                NEW_STATE,
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
        when(workflow.apply(eq("lead"), any(), eq("contact"), any())).thenReturn(CONTACTED_STATE);
        RecordInteractionCommand cmd =
                new RecordInteractionCommand(typeId, resultId, "Conversamos", Instant.now(), null);

        LeadDetail detail = service.recordInteraction(id, cmd, UUID.randomUUID(), false, false);

        assertThat(detail.status()).isEqualTo("CONTACTED");
        assertThat(detail.interactions()).hasSize(1);
        assertThat(detail.interactions().get(0).type()).isEqualTo("Ligação");
        assertThat(detail.interactions().get(0).result()).isEqualTo("Contato realizado");
    }

    @Test
    void qualifyMapsTheMainInterestIntoTheDetail() {
        UUID id = UUID.randomUUID();
        UUID responsible = UUID.randomUUID();
        Lead lead = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), responsible, null),
                Origin.create("WEBSITE", "Website", 1),
                NEW_STATE,
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
        when(workflow.apply(eq("lead"), any(), eq("qualify"), any())).thenReturn(QUALIFIED_STATE);

        LeadDetail detail = service.qualify(id, "Pacote corporativo", "bom perfil", responsible, false, false);

        assertThat(detail.status()).isEqualTo("QUALIFIED");
        assertThat(detail.qualification().mainInterest()).isEqualTo("Pacote corporativo");
        assertThat(detail.qualification().note()).isEqualTo("bom perfil");
    }

    @Test
    void indicatorsDeriveTotalFromStatusesAndResolveResponsibleNames() {
        UUID r1 = UUID.randomUUID();
        when(accessPolicy.visibleTo(any(), anyBoolean(), anyBoolean())).thenReturn((root, q, cb) -> null);
        when(indicatorQueries.countByStatus(any(), any(), any())).thenReturn(Map.of("NEW", 3L, "LOST", 2L));
        when(indicatorQueries.countByOrigin(any(), any(), any())).thenReturn(Map.of("Website", 4L));
        LinkedHashMap<UUID, Long> byResponsible = new LinkedHashMap<>();
        byResponsible.put(r1, 3L);
        byResponsible.put(null, 2L);
        when(indicatorQueries.countByResponsible(any(), any(), any())).thenReturn(byResponsible);
        when(indicatorQueries.countWaitingFirstContact(any(), any(), any())).thenReturn(1L);
        User user = mock(User.class);
        when(user.id()).thenReturn(r1);
        when(user.username()).thenReturn("ana");
        when(users.findAllById(any())).thenReturn(List.of(user));

        LeadIndicators view = service.indicators(UUID.randomUUID(), false, false, null, null);

        assertThat(view.total()).isEqualTo(5);
        assertThat(view.newLeads()).isEqualTo(3);
        assertThat(view.lost()).isEqualTo(2);
        assertThat(view.contacted()).isZero();
        assertThat(view.qualified()).isZero();
        assertThat(view.waitingFirstContact()).isEqualTo(1);
        assertThat(view.byOrigin()).containsExactly(new LeadIndicators.OriginCount("Website", 4L));
        assertThat(view.byResponsible())
                .containsExactlyInAnyOrder(
                        new LeadIndicators.ResponsibleCount("ana", 3L), new LeadIndicators.ResponsibleCount(null, 2L));
    }

    private static Lead visibleLead() {
        return Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), null, null),
                Origin.create("WEBSITE", "Website", 1),
                NEW_STATE,
                UUID.randomUUID());
    }
}
