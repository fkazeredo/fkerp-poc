package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.identity.UserRepository;
import java.math.BigDecimal;
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
    private ApplicationEventPublisher events;

    @InjectMocks
    private OpportunityService service;

    private static final UUID LEAD_ID = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Origin ORIGIN = Origin.create("WEBSITE", "Website", 1);

    private CreateOpportunityCommand command(UUID responsibleOverride) {
        return new CreateOpportunityCommand(
                LEAD_ID, responsibleOverride, "Software", new BigDecimal("1500.00"), null, "primeira nota");
    }

    @Test
    void createsOpportunityFromQualifiedLeadSeedingLeadData() {
        Lead lead = mock(Lead.class);
        when(lead.id()).thenReturn(LEAD_ID);
        when(lead.status()).thenReturn(LeadStatus.QUALIFIED);
        when(lead.name()).thenReturn("Maria");
        when(lead.origin()).thenReturn(ORIGIN);
        when(lead.mainInterest()).thenReturn("Pacote corporativo");
        when(lead.responsiblePersonId()).thenReturn(RESPONSIBLE);
        when(leads.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(leadAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(opportunities.findByLeadId(LEAD_ID)).thenReturn(Optional.empty());

        UUID id = service.create(command(null), ACTOR, true, false);

        assertThat(id).isNotNull();
        ArgumentCaptor<Opportunity> captor = ArgumentCaptor.forClass(Opportunity.class);
        verify(opportunities).save(captor.capture());
        Opportunity saved = captor.getValue();
        assertThat(saved.stage()).isEqualTo(OpportunityStage.NEW_OPPORTUNITY);
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
        when(lead.status()).thenReturn(LeadStatus.CONTACTED);
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
        when(lead.status()).thenReturn(LeadStatus.QUALIFIED);
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
        when(lead.status()).thenReturn(LeadStatus.QUALIFIED);
        when(leads.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(leadAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(opportunities.findByLeadId(LEAD_ID)).thenReturn(Optional.empty());
        UUID override = UUID.randomUUID();
        when(users.findById(override)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(override), ACTOR, true, false))
                .isInstanceOf(ResponsiblePersonNotFoundException.class);
        verify(opportunities, never()).save(any());
    }
}
