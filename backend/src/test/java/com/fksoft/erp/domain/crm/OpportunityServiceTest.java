package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.exception.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.LeadNotQualifiedForOpportunityException;
import com.fksoft.erp.domain.crm.exception.OpportunityAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.OpportunityAlreadyExistsForLeadException;
import com.fksoft.erp.domain.crm.exception.OpportunityNotFoundException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityCreated;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.service.LeadAccessPolicy;
import com.fksoft.erp.domain.crm.service.OpportunityAccessPolicy;
import com.fksoft.erp.domain.crm.service.OpportunityService;
import com.fksoft.erp.domain.crm.service.data.CreateOpportunityCommand;
import com.fksoft.erp.domain.crm.service.data.RecordActivityCommand;
import com.fksoft.erp.domain.crm.service.data.UpdateOpportunityDetailsCommand;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.workflow.WorkflowDefinition;
import com.fksoft.erp.domain.workflow.WorkflowEngine;
import com.fksoft.erp.domain.workflow.WorkflowState;
import com.fksoft.erp.domain.workflow.WorkflowStateCategory;
import com.fksoft.erp.domain.workflow.WorkflowStateRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
class OpportunityServiceTest {

    @Mock
    private OpportunityRepository opportunities;

    @Mock
    private LeadRepository leads;

    @Mock
    private UserRepository users;

    @Mock
    private LeadAccessPolicy leadAccessPolicy;

    @Mock
    private OpportunityAccessPolicy accessPolicy;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private WorkflowEngine workflow;

    @Mock
    private WorkflowStateRepository workflowStates;

    @InjectMocks
    private OpportunityService service;

    private static final UUID LEAD_ID = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Origin ORIGIN = Origin.create("WEBSITE", "Website", 1);
    private static final WorkflowState NEW_STATE = WorkflowState.of(
            WorkflowDefinition.of("opportunity", "Oportunidade"),
            "NEW_OPPORTUNITY",
            "Nova",
            WorkflowStateCategory.INITIAL,
            1);

    private CreateOpportunityCommand command(UUID responsibleOverride) {
        return new CreateOpportunityCommand(
                LEAD_ID, responsibleOverride, "Software", new BigDecimal("1500.00"), null, "primeira nota");
    }

    @Test
    void createsOpportunityFromQualifiedLeadSeedingLeadData() {
        Lead lead = mock(Lead.class);
        when(lead.id()).thenReturn(LEAD_ID);
        when(lead.status()).thenReturn("QUALIFIED");
        when(lead.name()).thenReturn("Maria");
        when(lead.origin()).thenReturn(ORIGIN);
        when(lead.mainInterest()).thenReturn("Pacote corporativo");
        when(lead.responsiblePersonId()).thenReturn(RESPONSIBLE);
        when(leads.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(leadAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(opportunities.findByLeadId(LEAD_ID)).thenReturn(Optional.empty());
        when(workflowStates.findByDefinition_CodeAndCode("opportunity", "NEW_OPPORTUNITY"))
                .thenReturn(Optional.of(NEW_STATE));

        UUID id = service.create(command(null), ACTOR, true, false);

        assertThat(id).isNotNull();
        ArgumentCaptor<Opportunity> captor = ArgumentCaptor.forClass(Opportunity.class);
        verify(opportunities).save(captor.capture());
        Opportunity saved = captor.getValue();
        assertThat(saved.stage()).isEqualTo("NEW_OPPORTUNITY");
        assertThat(saved.leadId()).isEqualTo(LEAD_ID);
        assertThat(saved.origin()).isEqualTo(ORIGIN);
        assertThat(saved.responsiblePersonId()).isEqualTo(RESPONSIBLE); // preserved from the lead
        assertThat(saved.mainInterest()).isEqualTo("Pacote corporativo");
        assertThat(saved.productType()).isEqualTo("Software");
        verify(events).publishEvent(any(OpportunityCreated.class));
    }

    @Test
    void rejectsCreatingFromANonQualifiedLead() {
        Lead lead = mock(Lead.class);
        when(lead.status()).thenReturn("CONTACTED");
        when(leads.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(leadAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(true);

        assertThatThrownBy(() -> service.create(command(null), ACTOR, true, false))
                .isInstanceOf(LeadNotQualifiedForOpportunityException.class);
        verify(opportunities, never()).save(any());
    }

    @Test
    void rejectsWhenTheSourceLeadIsNotVisible() {
        Lead lead = mock(Lead.class);
        when(leads.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(leadAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.create(command(null), ACTOR, false, false))
                .isInstanceOf(LeadAccessDeniedException.class);
        verify(opportunities, never()).save(any());
    }

    @Test
    void rejectsASecondOpportunityForTheSameLead() {
        Lead lead = mock(Lead.class);
        when(lead.id()).thenReturn(LEAD_ID);
        when(lead.status()).thenReturn("QUALIFIED");
        when(leads.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(leadAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(true);
        Opportunity existing = mock(Opportunity.class);
        when(existing.id()).thenReturn(UUID.randomUUID());
        when(opportunities.findByLeadId(LEAD_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(command(null), ACTOR, true, false))
                .isInstanceOf(OpportunityAlreadyExistsForLeadException.class);
        verify(opportunities, never()).save(any());
    }

    @Test
    void rejectsAnUnknownResponsibleOverride() {
        Lead lead = mock(Lead.class);
        when(lead.id()).thenReturn(LEAD_ID);
        when(lead.status()).thenReturn("QUALIFIED");
        when(leads.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(leadAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(opportunities.findByLeadId(LEAD_ID)).thenReturn(Optional.empty());
        UUID override = UUID.randomUUID();
        when(users.findById(override)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(override), ACTOR, true, false))
                .isInstanceOf(ResponsiblePersonNotFoundException.class);
        verify(opportunities, never()).save(any());
    }

    @Test
    void detailThrowsWhenOpportunityIsMissing() {
        UUID id = UUID.randomUUID();
        when(opportunities.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(id, ACTOR, true, false))
                .isInstanceOf(OpportunityNotFoundException.class);
    }

    @Test
    void detailThrowsWhenTheOpportunityIsNotVisible() {
        UUID id = UUID.randomUUID();
        when(opportunities.findById(id)).thenReturn(Optional.of(mock(Opportunity.class)));
        when(accessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.detail(id, ACTOR, false, false))
                .isInstanceOf(OpportunityAccessDeniedException.class);
    }

    @Test
    void markLostThrowsWhenTheOpportunityIsNotVisible() {
        UUID id = UUID.randomUUID();
        when(opportunities.findById(id)).thenReturn(Optional.of(mock(Opportunity.class)));
        when(accessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.markLost(id, UUID.randomUUID(), null, ACTOR, false, false))
                .isInstanceOf(OpportunityAccessDeniedException.class);
        verify(opportunities, never()).saveAndFlush(any());
    }

    @Test
    void changeStageThrowsWhenOpportunityIsMissing() {
        UUID id = UUID.randomUUID();
        when(opportunities.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStage(id, "DISCOVERY", ACTOR, true, false))
                .isInstanceOf(OpportunityNotFoundException.class);
    }

    @Test
    void changeStageThrowsWhenTheOpportunityIsNotVisible() {
        UUID id = UUID.randomUUID();
        when(opportunities.findById(id)).thenReturn(Optional.of(mock(Opportunity.class)));
        when(accessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.changeStage(id, "DISCOVERY", ACTOR, false, false))
                .isInstanceOf(OpportunityAccessDeniedException.class);
        verify(opportunities, never()).saveAndFlush(any());
    }

    @Test
    void recordActivityThrowsWhenOpportunityIsMissing() {
        UUID id = UUID.randomUUID();
        when(opportunities.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordActivity(id, activityCommand(), ACTOR, true, false))
                .isInstanceOf(OpportunityNotFoundException.class);
    }

    @Test
    void recordActivityThrowsWhenTheOpportunityIsNotVisible() {
        UUID id = UUID.randomUUID();
        when(opportunities.findById(id)).thenReturn(Optional.of(mock(Opportunity.class)));
        when(accessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.recordActivity(id, activityCommand(), ACTOR, false, false))
                .isInstanceOf(OpportunityAccessDeniedException.class);
        verify(opportunities, never()).saveAndFlush(any());
    }

    private RecordActivityCommand activityCommand() {
        return new RecordActivityCommand(UUID.randomUUID(), UUID.randomUUID(), "x", Instant.now(), null);
    }

    @Test
    void updateDetailsThrowsWhenOpportunityIsMissing() {
        UUID id = UUID.randomUUID();
        when(opportunities.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDetails(id, detailsCommand(), ACTOR, true, false))
                .isInstanceOf(OpportunityNotFoundException.class);
    }

    @Test
    void updateDetailsThrowsWhenTheOpportunityIsNotVisible() {
        UUID id = UUID.randomUUID();
        when(opportunities.findById(id)).thenReturn(Optional.of(mock(Opportunity.class)));
        when(accessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.updateDetails(id, detailsCommand(), ACTOR, false, false))
                .isInstanceOf(OpportunityAccessDeniedException.class);
        verify(opportunities, never()).saveAndFlush(any());
    }

    private UpdateOpportunityDetailsCommand detailsCommand() {
        return new UpdateOpportunityDetailsCommand(null, null, null, null);
    }
}
