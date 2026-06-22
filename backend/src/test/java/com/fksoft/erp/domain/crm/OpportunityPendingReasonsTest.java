package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityPendingReason;
import com.fksoft.erp.domain.crm.model.OpportunityPendingReasons;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link OpportunityPendingReasons} at fixed {@code now}/{@code today}: each input maps
 * to the expected reasons. The staleness window is 14 days; "stuck" is measured from the creation date.
 * The Opportunity is mocked to control its stage/dates independently of the persistence lifecycle.
 */
class OpportunityPendingReasonsTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-06-15");
    private static final Instant OLD = Instant.parse("2026-05-01T12:00:00Z"); // > 14 days before NOW
    private static final Instant RECENT = Instant.parse("2026-06-14T12:00:00Z"); // < 14 days before NOW

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
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null)).isEmpty();
    }

    @Test
    void oldWithNoRecentActivityIsWithoutRecentActivity() {
        Opportunity o = opportunity("PRODUCT_FIT", OLD, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null))
                .containsExactly(OpportunityPendingReason.WITHOUT_RECENT_ACTIVITY);
    }

    @Test
    void aRecentActivityRescuesAnOldOpportunity() {
        Opportunity o = opportunity("PRODUCT_FIT", OLD, null, null);
        Instant recentActivity = Instant.parse("2026-06-10T12:00:00Z"); // within the 14-day window
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, recentActivity)).isEmpty();
    }

    @Test
    void overdueNextActionIsPending() {
        Opportunity o = opportunity("PRODUCT_FIT", RECENT, TODAY.minusDays(1), null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null))
                .containsExactly(OpportunityPendingReason.OVERDUE_NEXT_ACTION);
    }

    @Test
    void stuckInNewAlsoFlagsWithoutRecentActivity() {
        Opportunity o = opportunity("NEW_OPPORTUNITY", OLD, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null))
                .containsExactlyInAnyOrder(
                        OpportunityPendingReason.WITHOUT_RECENT_ACTIVITY, OpportunityPendingReason.STUCK_IN_NEW);
    }

    @Test
    void stuckInDiscoveryAlsoFlagsWithoutRecentActivity() {
        Opportunity o = opportunity("DISCOVERY", OLD, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null))
                .containsExactlyInAnyOrder(
                        OpportunityPendingReason.WITHOUT_RECENT_ACTIVITY, OpportunityPendingReason.STUCK_IN_DISCOVERY);
    }

    @Test
    void readyForProposalIsPending() {
        Opportunity o = opportunity("READY_FOR_PROPOSAL", RECENT, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, RECENT))
                .containsExactly(OpportunityPendingReason.READY_FOR_PROPOSAL);
    }

    @Test
    void expectedCloseInThePastIsPending() {
        Opportunity o = opportunity("PRODUCT_FIT", RECENT, null, TODAY.minusDays(1));
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null))
                .containsExactly(OpportunityPendingReason.EXPECTED_CLOSE_OVERDUE);
    }

    @Test
    void aRecentlyCreatedActiveOpportunityHasNoReason() {
        Opportunity o = opportunity("PRODUCT_FIT", RECENT, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, RECENT)).isEmpty();
    }

    @Test
    void aRecentlyCreatedNewOpportunityIsNotYetStuck() {
        Opportunity o = opportunity("NEW_OPPORTUNITY", RECENT, null, null);
        assertThat(OpportunityPendingReasons.of(o, NOW, TODAY, null)).isEmpty();
    }
}
