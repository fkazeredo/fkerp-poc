package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityActivityResult;
import com.fksoft.erp.domain.crm.model.OpportunityActivityType;
import com.fksoft.erp.domain.crm.model.OpportunityLossReason;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.service.data.CreateOpportunityCommand;
import com.fksoft.erp.domain.workflow.WorkflowDefinition;
import com.fksoft.erp.domain.workflow.WorkflowState;
import com.fksoft.erp.domain.workflow.WorkflowStateCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain invariants of the Opportunity aggregate. Since the workflow reform, the pipeline transitions are
 * validated by the workflow engine in the application service; the entity exposes field-setting methods that
 * move it to a (pre-validated) target state and record the stage history. The transition guards (strict
 * forward funnel, no move from a terminal stage) are covered end-to-end by the API integration tests.
 */
class OpportunityTest {

    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private final Origin origin = Origin.create("WEBSITE", "Website", 1);
    private final OpportunityLossReason reason = OpportunityLossReason.NO_RESPONSE;

    private final WorkflowDefinition wf = WorkflowDefinition.of("opportunity", "Oportunidade");
    private final WorkflowState newState =
            WorkflowState.of(wf, "NEW_OPPORTUNITY", "Nova", WorkflowStateCategory.INITIAL, 1);
    private final WorkflowState discovery =
            WorkflowState.of(wf, "DISCOVERY", "Descoberta", WorkflowStateCategory.ACTIVE, 2);
    private final WorkflowState productFit =
            WorkflowState.of(wf, "PRODUCT_FIT", "Aderência", WorkflowStateCategory.ACTIVE, 3);
    private final WorkflowState readyForProposal =
            WorkflowState.of(wf, "READY_FOR_PROPOSAL", "Pronta", WorkflowStateCategory.ACTIVE, 4);
    private final WorkflowState won = WorkflowState.of(wf, "WON", "Ganha", WorkflowStateCategory.TERMINAL_POSITIVE, 5);
    private final WorkflowState lost =
            WorkflowState.of(wf, "LOST", "Perdida", WorkflowStateCategory.TERMINAL_NEGATIVE, 6);

    private Opportunity newOpportunity() {
        Lead lead = mock(Lead.class);
        when(lead.status()).thenReturn("QUALIFIED");
        when(lead.id()).thenReturn(UUID.randomUUID());
        when(lead.name()).thenReturn("Maria");
        when(lead.origin()).thenReturn(origin);
        when(lead.mainInterest()).thenReturn("Pacote corporativo");
        CreateOpportunityCommand command =
                new CreateOpportunityCommand(lead.id(), RESPONSIBLE, "Software", new BigDecimal("1500.00"), null, null);
        return Opportunity.createFromLead(lead, RESPONSIBLE, command, newState, CREATOR);
    }

    @Test
    void startsAtTheInitialStage() {
        Opportunity opportunity = newOpportunity();
        assertThat(opportunity.stage()).isEqualTo("NEW_OPPORTUNITY");
        assertThat(opportunity.currentState()).isSameAs(newState);
        assertThat(opportunity.mainInterest()).isEqualTo("Pacote corporativo");
    }

    @Test
    void applyLossSetsTheOutcomeAndRecordsTheMovement() {
        Opportunity opportunity = newOpportunity();

        opportunity.applyLoss(lost, reason, RESPONSIBLE, "sumiu");

        assertThat(opportunity.stage()).isEqualTo("LOST");
        assertThat(opportunity.currentState()).isSameAs(lost);
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
    void applyWinSetsTheOutcomeAndRecordsTheMovement() {
        Opportunity opportunity = newOpportunity();
        opportunity.applyStageAdvance(discovery, RESPONSIBLE);

        opportunity.applyWin(won, CREATOR);

        assertThat(opportunity.stage()).isEqualTo("WON");
        assertThat(opportunity.currentState()).isSameAs(won);
        var last = opportunity.stageChanges().get(opportunity.stageChanges().size() - 1);
        assertThat(last.fromStage()).isEqualTo("DISCOVERY");
        assertThat(last.toStage()).isEqualTo("WON");
        assertThat(last.changedBy()).isEqualTo(CREATOR);
    }

    @Test
    void applyStageAdvanceMovesForwardRecordingEachMovement() {
        Opportunity opportunity = newOpportunity();

        opportunity.applyStageAdvance(discovery, RESPONSIBLE);
        opportunity.applyStageAdvance(productFit, RESPONSIBLE);
        opportunity.applyStageAdvance(readyForProposal, RESPONSIBLE);

        assertThat(opportunity.stage()).isEqualTo("READY_FOR_PROPOSAL");
        assertThat(opportunity.currentState()).isSameAs(readyForProposal);
        assertThat(opportunity.stageChanges()).hasSize(3);
        assertThat(opportunity.stageChanges().get(0).toStage()).isEqualTo("DISCOVERY");
        assertThat(opportunity.stageChanges().get(2).toStage()).isEqualTo("READY_FOR_PROPOSAL");
    }

    @Test
    void recordsAnActivityKeepingHistoryAndWithoutMovingTheStage() {
        Opportunity opportunity = newOpportunity();

        opportunity.recordActivity(
                OpportunityActivityType.PHONE_CALL,
                OpportunityActivityResult.CLIENT_ENGAGED,
                "ligação inicial",
                Instant.parse("2026-06-10T13:00:00Z"),
                LocalDate.parse("2026-06-20"),
                RESPONSIBLE);

        assertThat(opportunity.activities()).hasSize(1);
        assertThat(opportunity.activities().get(0).type()).isEqualTo(OpportunityActivityType.PHONE_CALL);
        assertThat(opportunity.activities().get(0).registeredBy()).isEqualTo(RESPONSIBLE);
        assertThat(opportunity.nextActionDate()).isEqualTo(LocalDate.parse("2026-06-20"));
        assertThat(opportunity.stage()).isEqualTo("NEW_OPPORTUNITY"); // unchanged
    }

    @Test
    void recordingAnActivityWithoutNextActionKeepsThePreviousOne() {
        Opportunity opportunity = newOpportunity();
        opportunity.recordActivity(
                OpportunityActivityType.MEETING,
                OpportunityActivityResult.PRODUCT_FIT_IDENTIFIED,
                "reunião",
                Instant.parse("2026-06-10T13:00:00Z"),
                LocalDate.parse("2026-06-20"),
                RESPONSIBLE);

        opportunity.recordActivity(
                OpportunityActivityType.EMAIL,
                OpportunityActivityResult.WAITING_FOR_CLIENT,
                "e-mail",
                Instant.parse("2026-06-12T13:00:00Z"),
                null,
                RESPONSIBLE);

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
        assertThat(opportunity.stage()).isEqualTo("NEW_OPPORTUNITY"); // unchanged
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
