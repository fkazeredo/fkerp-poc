package com.fksoft.erp.domain.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.sales.exception.OpportunityNotReadyForProposalException;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Domain invariants of the Proposal aggregate factory (created from a READY_FOR_PROPOSAL Opportunity). */
class ProposalTest {

    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final UUID OPP_ID = UUID.randomUUID();
    private static final UUID LEAD_ID = UUID.randomUUID();

    private Opportunity opportunity(OpportunityStage stage) {
        Opportunity o = mock(Opportunity.class);
        when(o.stage()).thenReturn(stage);
        return o;
    }

    private CreateProposalCommand command() {
        return new CreateProposalCommand(
                OPP_ID, RESPONSIBLE, "Proposta corporativa", "anotação", LocalDate.parse("2026-12-31"), "termos");
    }

    @Test
    void createsFromReadyOpportunityAsDraft() {
        Opportunity o = opportunity(OpportunityStage.READY_FOR_PROPOSAL);
        when(o.id()).thenReturn(OPP_ID);
        when(o.leadId()).thenReturn(LEAD_ID);

        Proposal proposal = Proposal.createFromOpportunity(o, RESPONSIBLE, command(), CREATOR);

        assertThat(proposal.status()).isEqualTo(ProposalStatus.DRAFT);
        assertThat(proposal.isOpen()).isTrue();
        assertThat(proposal.opportunityId()).isEqualTo(OPP_ID);
        assertThat(proposal.leadId()).isEqualTo(LEAD_ID); // source Lead reference preserved
        assertThat(proposal.responsiblePersonId()).isEqualTo(RESPONSIBLE);
        assertThat(proposal.title()).isEqualTo("Proposta corporativa");
        assertThat(proposal.notes()).isEqualTo("anotação");
        assertThat(proposal.validUntil()).isEqualTo(LocalDate.parse("2026-12-31"));
        assertThat(proposal.commercialTerms()).isEqualTo("termos");
    }

    @ParameterizedTest
    @EnumSource(
            value = OpportunityStage.class,
            names = {"NEW_OPPORTUNITY", "DISCOVERY", "PRODUCT_FIT", "LOST"})
    void rejectsCreatingFromANonReadyOpportunity(OpportunityStage stage) {
        Opportunity o = opportunity(stage);

        assertThatThrownBy(() -> Proposal.createFromOpportunity(o, RESPONSIBLE, command(), CREATOR))
                .isInstanceOf(OpportunityNotReadyForProposalException.class);
    }
}
