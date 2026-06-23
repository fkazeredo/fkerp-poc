package com.fksoft.erp.domain.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.exception.OpportunityAccessDeniedException;
import com.fksoft.erp.domain.crm.exception.OpportunityNotFoundException;
import com.fksoft.erp.domain.crm.exception.ResponsiblePersonNotFoundException;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.service.OpportunityAccessPolicy;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.sales.exception.OpportunityNotReadyForProposalException;
import com.fksoft.erp.domain.sales.exception.ProposalAccessDeniedException;
import com.fksoft.erp.domain.sales.exception.ProposalAlreadyExistsForOpportunityException;
import com.fksoft.erp.domain.sales.exception.ProposalNotFoundException;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalCreated;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.domain.sales.service.ProposalAccessPolicy;
import com.fksoft.erp.domain.sales.service.ProposalItemTypeService;
import com.fksoft.erp.domain.sales.service.ProposalService;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import com.fksoft.erp.domain.sales.service.data.UpdateProposalCommand;
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
class ProposalServiceTest {

    @Mock
    private ProposalRepository proposals;

    @Mock
    private ProposalAccessPolicy accessPolicy;

    @Mock
    private OpportunityRepository opportunities;

    @Mock
    private OpportunityAccessPolicy opportunityAccessPolicy;

    @Mock
    private LeadRepository leads;

    @Mock
    private UserRepository users;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private ProposalItemTypeService itemTypes;

    @InjectMocks
    private ProposalService service;

    private static final UUID OPP_ID = UUID.randomUUID();
    private static final UUID LEAD_ID = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    private CreateProposalCommand command(UUID responsibleOverride) {
        return new CreateProposalCommand(OPP_ID, responsibleOverride, "Proposta", null, null, null);
    }

    private Opportunity readyOpportunity() {
        Opportunity o = mock(Opportunity.class);
        when(o.stage()).thenReturn(OpportunityStage.READY_FOR_PROPOSAL);
        return o;
    }

    @Test
    void createsProposalFromReadyOpportunitySeedingOpportunityData() {
        Opportunity opp = readyOpportunity();
        when(opp.id()).thenReturn(OPP_ID);
        when(opp.leadId()).thenReturn(LEAD_ID);
        when(opp.responsiblePersonId()).thenReturn(RESPONSIBLE);
        when(opportunities.findById(OPP_ID)).thenReturn(Optional.of(opp));
        when(opportunityAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);
        when(proposals.findFirstByOpportunityIdAndStatusIn(any(), any())).thenReturn(Optional.empty());

        UUID id = service.create(command(null), ACTOR, true, false);

        assertThat(id).isNotNull();
        ArgumentCaptor<Proposal> captor = ArgumentCaptor.forClass(Proposal.class);
        verify(proposals).save(captor.capture());
        Proposal saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(ProposalStatus.DRAFT);
        assertThat(saved.opportunityId()).isEqualTo(OPP_ID);
        assertThat(saved.leadId()).isEqualTo(LEAD_ID);
        assertThat(saved.responsiblePersonId()).isEqualTo(RESPONSIBLE); // preserved from the opportunity
        assertThat(saved.title()).isEqualTo("Proposta");
        verify(events).publishEvent(any(ProposalCreated.class));
    }

    @Test
    void rejectsWhenTheSourceOpportunityIsMissing() {
        when(opportunities.findById(OPP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(null), ACTOR, true, false))
                .isInstanceOf(OpportunityNotFoundException.class);
        verify(proposals, never()).save(any());
    }

    @Test
    void rejectsWhenTheSourceOpportunityIsNotVisible() {
        when(opportunities.findById(OPP_ID)).thenReturn(Optional.of(mock(Opportunity.class)));
        when(opportunityAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.create(command(null), ACTOR, false, false))
                .isInstanceOf(OpportunityAccessDeniedException.class);
        verify(proposals, never()).save(any());
    }

    @Test
    void rejectsWhenTheOpportunityIsNotReadyForProposal() {
        Opportunity opp = mock(Opportunity.class);
        when(opp.stage()).thenReturn(OpportunityStage.PRODUCT_FIT);
        when(opportunities.findById(OPP_ID)).thenReturn(Optional.of(opp));
        when(opportunityAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(command(null), ACTOR, true, false))
                .isInstanceOf(OpportunityNotReadyForProposalException.class);
        verify(proposals, never()).save(any());
    }

    @Test
    void rejectsASecondActiveProposalForTheOpportunity() {
        Opportunity opp = readyOpportunity();
        when(opp.id()).thenReturn(OPP_ID);
        when(opportunities.findById(OPP_ID)).thenReturn(Optional.of(opp));
        when(opportunityAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);
        Proposal existing = mock(Proposal.class);
        when(existing.id()).thenReturn(UUID.randomUUID());
        when(proposals.findFirstByOpportunityIdAndStatusIn(any(), any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(command(null), ACTOR, true, false))
                .isInstanceOf(ProposalAlreadyExistsForOpportunityException.class);
        verify(proposals, never()).save(any());
    }

    @Test
    void rejectsAnUnknownResponsibleOverride() {
        Opportunity opp = readyOpportunity();
        when(opp.id()).thenReturn(OPP_ID);
        when(opportunities.findById(OPP_ID)).thenReturn(Optional.of(opp));
        when(opportunityAccessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);
        when(proposals.findFirstByOpportunityIdAndStatusIn(any(), any())).thenReturn(Optional.empty());
        UUID override = UUID.randomUUID();
        when(users.findById(override)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(override), ACTOR, true, false))
                .isInstanceOf(ResponsiblePersonNotFoundException.class);
        verify(proposals, never()).save(any());
    }

    @Test
    void detailThrowsWhenProposalIsMissing() {
        UUID id = UUID.randomUUID();
        when(proposals.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(id, ACTOR, true, false)).isInstanceOf(ProposalNotFoundException.class);
    }

    @Test
    void detailThrowsWhenTheProposalIsNotVisible() {
        UUID id = UUID.randomUUID();
        when(proposals.findById(id)).thenReturn(Optional.of(mock(Proposal.class)));
        when(accessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.detail(id, ACTOR, false, false))
                .isInstanceOf(ProposalAccessDeniedException.class);
    }

    @Test
    void addItemThrowsWhenTheProposalIsMissing() {
        UUID id = UUID.randomUUID();
        when(proposals.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(id, itemCommand(), ACTOR, true, false))
                .isInstanceOf(ProposalNotFoundException.class);
        verify(proposals, never()).saveAndFlush(any());
    }

    @Test
    void addItemThrowsWhenTheProposalIsNotVisible() {
        UUID id = UUID.randomUUID();
        when(proposals.findById(id)).thenReturn(Optional.of(mock(Proposal.class)));
        when(accessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.addItem(id, itemCommand(), ACTOR, false, false))
                .isInstanceOf(ProposalAccessDeniedException.class);
        verify(proposals, never()).saveAndFlush(any());
    }

    @Test
    void updateDetailsThrowsWhenTheProposalIsMissing() {
        UUID id = UUID.randomUUID();
        when(proposals.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDetails(id, updateCommand(), ACTOR, true, false))
                .isInstanceOf(ProposalNotFoundException.class);
        verify(proposals, never()).saveAndFlush(any());
    }

    @Test
    void updateDetailsThrowsWhenTheProposalIsNotVisible() {
        UUID id = UUID.randomUUID();
        when(proposals.findById(id)).thenReturn(Optional.of(mock(Proposal.class)));
        when(accessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.updateDetails(id, updateCommand(), ACTOR, false, false))
                .isInstanceOf(ProposalAccessDeniedException.class);
        verify(proposals, never()).saveAndFlush(any());
    }

    @Test
    void submitForReviewThrowsWhenTheProposalIsMissing() {
        UUID id = UUID.randomUUID();
        when(proposals.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitForReview(id, ACTOR, true, false))
                .isInstanceOf(ProposalNotFoundException.class);
        verify(proposals, never()).saveAndFlush(any());
    }

    @Test
    void submitForReviewThrowsWhenTheProposalIsNotVisible() {
        UUID id = UUID.randomUUID();
        when(proposals.findById(id)).thenReturn(Optional.of(mock(Proposal.class)));
        when(accessPolicy.canSee(any(), any(), anyBoolean(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> service.submitForReview(id, ACTOR, false, false))
                .isInstanceOf(ProposalAccessDeniedException.class);
        verify(proposals, never()).saveAndFlush(any());
    }

    private ProposalItemCommand itemCommand() {
        return new ProposalItemCommand(
                ProposalItemTypeFixtures.OTHER.id(), "linha", 1, new BigDecimal("10.00"), null, null);
    }

    private UpdateProposalCommand updateCommand() {
        return new UpdateProposalCommand(null, null, null, null, null);
    }
}
