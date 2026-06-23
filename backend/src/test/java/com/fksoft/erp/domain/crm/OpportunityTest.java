package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.exception.OpportunityCannotBeMarkedLostException;
import com.fksoft.erp.domain.crm.exception.OpportunityStageTransitionException;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityActivityResult;
import com.fksoft.erp.domain.crm.model.OpportunityActivityType;
import com.fksoft.erp.domain.crm.model.OpportunityLossReason;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.service.data.CreateOpportunityCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain invariants of the Opportunity aggregate. The pipeline is a fixed enum state machine with pre-defined
 * transitions enforced on the entity: a strict forward funnel ({@code NEW_OPPORTUNITY → DISCOVERY → PRODUCT_FIT
 * → READY_FOR_PROPOSAL}), lose from any non-terminal stage, win when an order is created. Covers the
 * transitions, their guards (happy + sad paths) and the stage history.
 */
class OpportunityTest {

    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private final Origin origin = Origin.create("WEBSITE", "Website", 1);
    private final OpportunityLossReason reason = OpportunityLossReason.create("NO_RESPONSE", "Sem resposta", 1);
    private final OpportunityActivityType phoneCall = OpportunityActivityType.create("PHONE_CALL", "Ligação", 1);
    private final OpportunityActivityType meeting = OpportunityActivityType.create("MEETING", "Reunião", 2);
    private final OpportunityActivityType email = OpportunityActivityType.create("EMAIL", "E-mail", 3);
    private final OpportunityActivityResult clientEngaged =
            OpportunityActivityResult.create("CLIENT_ENGAGED", "Cliente engajado", 1);
    private final OpportunityActivityResult productFitResult =
            OpportunityActivityResult.create("PRODUCT_FIT_IDENTIFIED", "Aderência identificada", 2);
    private final OpportunityActivityResult waitingForClient =
            OpportunityActivityResult.create("WAITING_FOR_CLIENT", "Aguardando cliente", 3);

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
    void startsAtTheInitialStage() {
        Opportunity opportunity = newOpportunity();
        assertThat(opportunity.stage()).isEqualTo(OpportunityStage.NEW_OPPORTUNITY);
        assertThat(opportunity.mainInterest()).isEqualTo("Pacote corporativo");
    }

    @Test
    void applyLossSetsTheOutcomeAndRecordsTheMovement() {
        Opportunity opportunity = newOpportunity();

        opportunity.applyLoss(reason, RESPONSIBLE, "sumiu");

        assertThat(opportunity.stage()).isEqualTo(OpportunityStage.LOST);
        assertThat(opportunity.lossReason()).isEqualTo(reason);
        assertThat(opportunity.lostBy()).isEqualTo(RESPONSIBLE);
        assertThat(opportunity.lostAt()).isNotNull();
        assertThat(opportunity.lossNote()).isEqualTo("sumiu");
        assertThat(opportunity.stageChanges()).hasSize(1);
        assertThat(opportunity.stageChanges().get(0).fromStage()).isEqualTo("NEW_OPPORTUNITY");
        assertThat(opportunity.stageChanges().get(0).toStage()).isEqualTo("LOST");
        assertThat(opportunity.stageChanges().get(0).changedBy()).isEqualTo(RESPONSIBLE);
    }

    @Test
    void losingAnAlreadyTerminalOpportunityIsRejected() {
        Opportunity opportunity = newOpportunity();
        opportunity.applyLoss(reason, RESPONSIBLE, null);
        assertThatThrownBy(() -> opportunity.applyLoss(reason, RESPONSIBLE, null))
                .isInstanceOf(OpportunityCannotBeMarkedLostException.class);
    }

    @Test
    void applyWinSetsTheOutcomeAndRecordsTheMovement() {
        Opportunity opportunity = newOpportunity();
        opportunity.applyStageAdvance(RESPONSIBLE);

        opportunity.applyWin(CREATOR);

        assertThat(opportunity.stage()).isEqualTo(OpportunityStage.WON);
        var last = opportunity.stageChanges().get(opportunity.stageChanges().size() - 1);
        assertThat(last.fromStage()).isEqualTo("DISCOVERY");
        assertThat(last.toStage()).isEqualTo("WON");
        assertThat(last.changedBy()).isEqualTo(CREATOR);
    }

    @Test
    void applyStageAdvanceMovesForwardRecordingEachMovement() {
        Opportunity opportunity = newOpportunity();

        opportunity.applyStageAdvance(RESPONSIBLE);
        opportunity.applyStageAdvance(RESPONSIBLE);
        opportunity.applyStageAdvance(RESPONSIBLE);

        assertThat(opportunity.stage()).isEqualTo(OpportunityStage.READY_FOR_PROPOSAL);
        assertThat(opportunity.stageChanges()).hasSize(3);
        assertThat(opportunity.stageChanges().get(0).toStage()).isEqualTo("DISCOVERY");
        assertThat(opportunity.stageChanges().get(2).toStage()).isEqualTo("READY_FOR_PROPOSAL");
    }

    @Test
    void advancingPastTheLastActiveStageIsRejected() {
        Opportunity opportunity = newOpportunity();
        opportunity.applyStageAdvance(RESPONSIBLE); // DISCOVERY
        opportunity.applyStageAdvance(RESPONSIBLE); // PRODUCT_FIT
        opportunity.applyStageAdvance(RESPONSIBLE); // READY_FOR_PROPOSAL
        assertThatThrownBy(() -> opportunity.applyStageAdvance(RESPONSIBLE))
                .isInstanceOf(OpportunityStageTransitionException.class);
    }

    @Test
    void recordsAnActivityKeepingHistoryAndWithoutMovingTheStage() {
        Opportunity opportunity = newOpportunity();

        opportunity.recordActivity(
                phoneCall,
                clientEngaged,
                "ligação inicial",
                Instant.parse("2026-06-10T13:00:00Z"),
                LocalDate.parse("2026-06-20"),
                RESPONSIBLE);

        assertThat(opportunity.activities()).hasSize(1);
        assertThat(opportunity.activities().get(0).type().code()).isEqualTo("PHONE_CALL");
        assertThat(opportunity.activities().get(0).registeredBy()).isEqualTo(RESPONSIBLE);
        assertThat(opportunity.nextActionDate()).isEqualTo(LocalDate.parse("2026-06-20"));
        assertThat(opportunity.stage()).isEqualTo(OpportunityStage.NEW_OPPORTUNITY); // unchanged
    }

    @Test
    void recordingAnActivityWithoutNextActionKeepsThePreviousOne() {
        Opportunity opportunity = newOpportunity();
        opportunity.recordActivity(
                meeting,
                productFitResult,
                "reunião",
                Instant.parse("2026-06-10T13:00:00Z"),
                LocalDate.parse("2026-06-20"),
                RESPONSIBLE);

        opportunity.recordActivity(
                email, waitingForClient, "e-mail", Instant.parse("2026-06-12T13:00:00Z"), null, RESPONSIBLE);

        assertThat(opportunity.activities()).hasSize(2);
        assertThat(opportunity.nextActionDate()).isEqualTo(LocalDate.parse("2026-06-20"));
    }

    @Test
    void updatesCommercialDetailsWithoutTouchingMainInterestOrStage() {
        Opportunity opportunity = newOpportunity(); // mainInterest "Pacote corporativo", stage NEW

        opportunity.updateCommercialDetails(
                new BigDecimal("9000.00"), LocalDate.parse("2026-12-01"), "Novo produto", "nota", CREATOR);

        assertThat(opportunity.estimatedValue()).isEqualByComparingTo("9000.00");
        assertThat(opportunity.expectedCloseDate()).isEqualTo(LocalDate.parse("2026-12-01"));
        assertThat(opportunity.productType()).isEqualTo("Novo produto");
        assertThat(opportunity.notes()).isEqualTo("nota");
        assertThat(opportunity.mainInterest()).isEqualTo("Pacote corporativo"); // unchanged
        assertThat(opportunity.stage()).isEqualTo(OpportunityStage.NEW_OPPORTUNITY); // unchanged
    }

    @Test
    void clearsCommercialFieldsWhenGivenNull() {
        Opportunity opportunity = newOpportunity(); // seeded productType "Software", estimatedValue 1500

        opportunity.updateCommercialDetails(null, null, null, null, CREATOR);

        assertThat(opportunity.estimatedValue()).isNull();
        assertThat(opportunity.expectedCloseDate()).isNull();
        assertThat(opportunity.productType()).isNull();
        assertThat(opportunity.notes()).isNull();
    }
}
