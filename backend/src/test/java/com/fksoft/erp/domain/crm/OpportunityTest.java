package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.exception.OpportunityCannotBeMarkedLostException;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.LossReason;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.service.data.CreateOpportunityCommand;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domain invariants of the Opportunity aggregate (the loss transition). */
class OpportunityTest {

    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private final Origin origin = Origin.create("WEBSITE", "Website", 1);
    private final LossReason reason = LossReason.create("NO_RESPONSE", "Sem resposta", 1);

    private Opportunity newOpportunity() {
        Lead lead = mock(Lead.class);
        when(lead.status()).thenReturn(LeadStatus.QUALIFIED);
        when(lead.id()).thenReturn(UUID.randomUUID());
        when(lead.name()).thenReturn("Maria");
        when(lead.origin()).thenReturn(origin);
        when(lead.mainInterest()).thenReturn("Pacote corporativo");
        CreateOpportunityCommand command =
                new CreateOpportunityCommand(lead.id(), RESPONSIBLE, "Software", new BigDecimal("1500.00"), null, null);
        return Opportunity.createFromLead(lead, RESPONSIBLE, command, CREATOR);
    }

    @Test
    void marksAsLostWithReasonAndKeepsHistory() {
        Opportunity opportunity = newOpportunity();

        opportunity.markLost(reason, RESPONSIBLE, "sumiu");

        assertThat(opportunity.stage()).isEqualTo(OpportunityStage.LOST);
        assertThat(opportunity.lossReason()).isEqualTo(reason);
        assertThat(opportunity.lostBy()).isEqualTo(RESPONSIBLE);
        assertThat(opportunity.lostAt()).isNotNull();
        assertThat(opportunity.lossNote()).isEqualTo("sumiu");
    }

    @Test
    void rejectsMarkingAnAlreadyLostOpportunityLost() {
        Opportunity opportunity = newOpportunity();
        opportunity.markLost(reason, RESPONSIBLE, null);

        assertThatThrownBy(() -> opportunity.markLost(reason, RESPONSIBLE, null))
                .isInstanceOf(OpportunityCannotBeMarkedLostException.class);
    }
}
