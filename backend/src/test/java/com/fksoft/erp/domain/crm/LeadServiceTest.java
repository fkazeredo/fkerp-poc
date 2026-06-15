package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.identity.User;
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
    private LossReasonRepository lossReasons;

    @Mock
    private UserRepository users;

    @Mock
    private LeadAccessPolicy accessPolicy;

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

        when(accessPolicy.visibleTo(any(), anyBoolean())).thenReturn((root, query, cb) -> null);
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
                new LeadSearchCriteria(null, null, null, false, null, null, null), pageable, UUID.randomUUID(), false);

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
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean())).thenReturn(true);

        LeadDetailView view = service.detail(id, UUID.randomUUID(), false);

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

        assertThatThrownBy(() -> service.detail(id, UUID.randomUUID(), false))
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
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.detail(id, UUID.randomUUID(), false))
                .isInstanceOf(LeadAccessDeniedException.class);
    }
}
