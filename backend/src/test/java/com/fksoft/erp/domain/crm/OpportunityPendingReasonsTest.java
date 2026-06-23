package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityPendingReasons;
import com.fksoft.erp.domain.workflow.WorkflowAttentionRule;
import com.fksoft.erp.domain.workflow.WorkflowDefinition;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link OpportunityPendingReasons} at fixed {@code now}/{@code today}: each input maps to
 * the expected reason codes, driven by the configurable attention rules (the staleness window is the rule's
 * threshold; "stuck" is measured from the creation date). The Opportunity is mocked to control its
 * stage/dates independently of the persistence lifecycle. The last two tests prove the data-driven behavior:
 * disabling a rule drops its reason, and changing its threshold moves the cutoff.
 */
class OpportunityPendingReasonsTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-06-15");
    private static final Instant OLD = Instant.parse("2026-05-01T12:00:00Z"); // > 14 days before NOW
    private static final Instant RECENT = Instant.parse("2026-06-14T12:00:00Z"); // < 14 days before NOW

    private static final WorkflowDefinition WF = WorkflowDefinition.of("opportunity", "Oportunidade");
    private static final List<WorkflowAttentionRule> RULES = List.of(
            WorkflowAttentionRule.of(WF, "NO_RECENT_ACTIVITY", 14, null, "WITHOUT_RECENT_ACTIVITY", "Sem atividade", 1),
            WorkflowAttentionRule.of(WF, "NEXT_ACTION_OVERDUE", null, null, "OVERDUE_NEXT_ACTION", "Ação vencida", 2),
            WorkflowAttentionRule.of(WF, "IN_STATE_LONGER_THAN", 14, "NEW_OPPORTUNITY", "STUCK_IN_NEW", "Parada", 3),
            WorkflowAttentionRule.of(WF, "IN_STATE_LONGER_THAN", 14, "DISCOVERY", "STUCK_IN_DISCOVERY", "Parada", 4),
            WorkflowAttentionRule.of(WF, "IN_STATE", null, "READY_FOR_PROPOSAL", "READY_FOR_PROPOSAL", "Pronta", 5),
            WorkflowAttentionRule.of(WF, "EXPECTED_CLOSE_OVERDUE", null, null, "EXPECTED_CLOSE_OVERDUE", "Venc.", 6));

    private Opportunity opportunity(
            String stage, Instant createdAt, LocalDate nextActionDate, LocalDate expectedCloseDate) {
        Opportunity o = mock(Opportunity.class);
        when(o.stage()).thenReturn(stage);
        when(o.createdAt()).thenReturn(createdAt);
        when(o.nextActionDate()).thenReturn(nextActionDate);
        when(o.expectedCloseDate()).thenReturn(expectedCloseDate);
        return o;
    }

    @Test
    void lostIsNeverPending() {
        Opportunity o = opportunity("LOST", OLD, LocalDate.parse("2026-06-01"), TODAY.minusDays(5));
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null, RULES)).isEmpty();
    }

    @Test
    void oldWithNoRecentActivityIsWithoutRecentActivity() {
        Opportunity o = opportunity("PRODUCT_FIT", OLD, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null, RULES)).containsExactly("WITHOUT_RECENT_ACTIVITY");
    }

    @Test
    void aRecentActivityRescuesAnOldOpportunity() {
        Opportunity o = opportunity("PRODUCT_FIT", OLD, null, null);
        Instant recentActivity = Instant.parse("2026-06-10T12:00:00Z"); // within the 14-day window
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, recentActivity, RULES))
                .isEmpty();
    }

    @Test
    void overdueNextActionIsPending() {
        Opportunity o = opportunity("PRODUCT_FIT", RECENT, TODAY.minusDays(1), null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null, RULES)).containsExactly("OVERDUE_NEXT_ACTION");
    }

    @Test
    void stuckInNewAlsoFlagsWithoutRecentActivity() {
        Opportunity o = opportunity("NEW_OPPORTUNITY", OLD, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null, RULES))
                .containsExactlyInAnyOrder("WITHOUT_RECENT_ACTIVITY", "STUCK_IN_NEW");
    }

    @Test
    void stuckInDiscoveryAlsoFlagsWithoutRecentActivity() {
        Opportunity o = opportunity("DISCOVERY", OLD, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null, RULES))
                .containsExactlyInAnyOrder("WITHOUT_RECENT_ACTIVITY", "STUCK_IN_DISCOVERY");
    }

    @Test
    void readyForProposalIsPending() {
        Opportunity o = opportunity("READY_FOR_PROPOSAL", RECENT, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, RECENT, RULES)).containsExactly("READY_FOR_PROPOSAL");
    }

    @Test
    void expectedCloseInThePastIsPending() {
        Opportunity o = opportunity("PRODUCT_FIT", RECENT, null, TODAY.minusDays(1));
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null, RULES)).containsExactly("EXPECTED_CLOSE_OVERDUE");
    }

    @Test
    void aRecentlyCreatedActiveOpportunityHasNoReason() {
        Opportunity o = opportunity("PRODUCT_FIT", RECENT, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, RECENT, RULES)).isEmpty();
    }

    @Test
    void aRecentlyCreatedNewOpportunityIsNotYetStuck() {
        Opportunity o = opportunity("NEW_OPPORTUNITY", RECENT, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null, RULES)).isEmpty();
    }

    @Test
    void disablingARuleDropsItsReason() {
        // Only the next-action rule is active → an old, stale Opportunity yields no "without recent activity".
        List<WorkflowAttentionRule> onlyNextAction =
                List.of(WorkflowAttentionRule.of(WF, "NEXT_ACTION_OVERDUE", null, null, "OVERDUE_NEXT_ACTION", "x", 1));
        Opportunity o = opportunity("PRODUCT_FIT", OLD, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null, onlyNextAction))
                .isEmpty();
    }

    @Test
    void changingTheThresholdMovesTheCutoff() {
        // With a 30-day window, an Opportunity created ~14 days ago is no longer "stale".
        List<WorkflowAttentionRule> wide = List.of(
                WorkflowAttentionRule.of(WF, "NO_RECENT_ACTIVITY", 30, null, "WITHOUT_RECENT_ACTIVITY", "x", 1));
        Opportunity o = opportunity("PRODUCT_FIT", OLD, null, null); // OLD is ~45 days before NOW → still stale at 30
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null, wide)).containsExactly("WITHOUT_RECENT_ACTIVITY");
        // But a 60-day window rescues it.
        List<WorkflowAttentionRule> wider = List.of(
                WorkflowAttentionRule.of(WF, "NO_RECENT_ACTIVITY", 60, null, "WITHOUT_RECENT_ACTIVITY", "x", 1));
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null, wider)).isEmpty();
    }
}
