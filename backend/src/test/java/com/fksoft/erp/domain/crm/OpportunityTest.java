package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.exception.OpportunityCannotBeMarkedLostException;
import com.fksoft.erp.domain.crm.exception.OpportunityStageTransitionException;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.LossReason;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityActivityResult;
import com.fksoft.erp.domain.crm.model.OpportunityActivityType;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.service.data.CreateOpportunityCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    @Test
    void marksLostRecordingTheMovementToLost() {
        Opportunity opportunity = newOpportunity();

        opportunity.markLost(reason, RESPONSIBLE, null);

        assertThat(opportunity.stageChanges()).hasSize(1);
        assertThat(opportunity.stageChanges().get(0).fromStage()).isEqualTo(OpportunityStage.NEW_OPPORTUNITY);
        assertThat(opportunity.stageChanges().get(0).toStage()).isEqualTo(OpportunityStage.LOST);
        assertThat(opportunity.stageChanges().get(0).changedBy()).isEqualTo(RESPONSIBLE);
    }

    @Test
    void advancesForwardThroughTheFunnelRecordingEachMovement() {
        Opportunity opportunity = newOpportunity();

        opportunity.moveToStage(OpportunityStage.DISCOVERY, RESPONSIBLE);
        opportunity.moveToStage(OpportunityStage.PRODUCT_FIT, RESPONSIBLE);
        opportunity.moveToStage(OpportunityStage.READY_FOR_PROPOSAL, RESPONSIBLE);

        assertThat(opportunity.stage()).isEqualTo(OpportunityStage.READY_FOR_PROPOSAL);
        assertThat(opportunity.stageChanges()).hasSize(3);
        assertThat(opportunity.stageChanges().get(0).toStage()).isEqualTo(OpportunityStage.DISCOVERY);
        assertThat(opportunity.stageChanges().get(2).toStage()).isEqualTo(OpportunityStage.READY_FOR_PROPOSAL);
    }

    @Test
    void rejectsMovingBackward() {
        Opportunity opportunity = newOpportunity();
        opportunity.moveToStage(OpportunityStage.DISCOVERY, RESPONSIBLE);

        assertThatThrownBy(() -> opportunity.moveToStage(OpportunityStage.NEW_OPPORTUNITY, RESPONSIBLE))
                .isInstanceOf(OpportunityStageTransitionException.class);
    }

    @Test
    void rejectsSkippingAStage() {
        Opportunity opportunity = newOpportunity();

        assertThatThrownBy(() -> opportunity.moveToStage(OpportunityStage.PRODUCT_FIT, RESPONSIBLE))
                .isInstanceOf(OpportunityStageTransitionException.class);
    }

    @Test
    void rejectsMovingToLostThroughTheStageTransition() {
        Opportunity opportunity = newOpportunity();

        assertThatThrownBy(() -> opportunity.moveToStage(OpportunityStage.LOST, RESPONSIBLE))
                .isInstanceOf(OpportunityStageTransitionException.class);
    }

    @Test
    void rejectsMovingAStageFromLost() {
        Opportunity opportunity = newOpportunity();
        opportunity.markLost(reason, RESPONSIBLE, null);

        assertThatThrownBy(() -> opportunity.moveToStage(OpportunityStage.DISCOVERY, RESPONSIBLE))
                .isInstanceOf(OpportunityStageTransitionException.class);
    }

    @Test
    void rejectsMovingToTheSameStage() {
        Opportunity opportunity = newOpportunity();

        assertThatThrownBy(() -> opportunity.moveToStage(OpportunityStage.NEW_OPPORTUNITY, RESPONSIBLE))
                .isInstanceOf(OpportunityStageTransitionException.class);
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
        assertThat(opportunity.stage()).isEqualTo(OpportunityStage.NEW_OPPORTUNITY); // unchanged
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
