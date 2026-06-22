package com.fksoft.erp.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.booking.model.BookingPendingReason;
import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestPendingReasons;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link BookingRequestPendingReasons} at fixed {@code now}/{@code today}: each input maps to
 * the expected reasons. The staleness window is 7 days. The Booking Request is mocked to control its status /
 * operator / last-attempt / next-action independently of the persistence lifecycle; the item-derived flags
 * (failed / requiring-pending) are passed in.
 */
class BookingRequestPendingReasonsTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-06-15");
    private static final Instant OLD = Instant.parse("2026-06-01T12:00:00Z"); // > 7 days before NOW
    private static final Instant RECENT = Instant.parse("2026-06-12T12:00:00Z"); // < 7 days before NOW
    private static final UUID OPERATOR = UUID.randomUUID();

    private BookingRequest request(
            BookingRequestStatus status, UUID operatorId, Instant lastAttemptAt, LocalDate nextActionDate) {
        BookingRequest r = mock(BookingRequest.class);
        when(r.status()).thenReturn(status);
        when(r.bookingOperatorId()).thenReturn(operatorId);
        when(r.lastAttemptAt()).thenReturn(lastAttemptAt);
        when(r.nextActionDate()).thenReturn(nextActionDate);
        return r;
    }

    @Test
    void confirmedIsNeverPending() {
        BookingRequest r = request(BookingRequestStatus.CONFIRMED, null, OLD, TODAY.minusDays(3));
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, true, true)).isEmpty();
    }

    @Test
    void cancelledIsNeverPending() {
        BookingRequest r = request(BookingRequestStatus.CANCELLED, null, OLD, TODAY.minusDays(3));
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, true, true)).isEmpty();
    }

    @Test
    void unassignedOperatorIsPending() {
        // In progress with a recent attempt and an operator-less request → only the unassigned reason.
        BookingRequest r = request(BookingRequestStatus.IN_PROGRESS, null, RECENT, null);
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, false, false))
                .containsExactly(BookingPendingReason.UNASSIGNED_OPERATOR);
    }

    @Test
    void pendingWithoutAttemptIsPending() {
        BookingRequest r = request(BookingRequestStatus.PENDING, OPERATOR, null, null);
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, false, false))
                .containsExactly(BookingPendingReason.PENDING_WITHOUT_ATTEMPT);
    }

    @Test
    void inProgressWithAStaleAttemptIsPending() {
        BookingRequest r = request(BookingRequestStatus.IN_PROGRESS, OPERATOR, OLD, null);
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, false, false))
                .containsExactly(BookingPendingReason.IN_PROGRESS_WITHOUT_RECENT_ATTEMPT);
    }

    @Test
    void inProgressWithNoAttemptAtAllIsPending() {
        BookingRequest r = request(BookingRequestStatus.IN_PROGRESS, OPERATOR, null, null);
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, false, false))
                .containsExactly(BookingPendingReason.IN_PROGRESS_WITHOUT_RECENT_ATTEMPT);
    }

    @Test
    void inProgressWithARecentAttemptIsNotPending() {
        BookingRequest r = request(BookingRequestStatus.IN_PROGRESS, OPERATOR, RECENT, null);
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, false, false)).isEmpty();
    }

    @Test
    void aFailedItemIsPending() {
        BookingRequest r = request(BookingRequestStatus.FAILED, OPERATOR, RECENT, null);
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, true, false))
                .containsExactly(BookingPendingReason.HAS_FAILED_ITEM);
    }

    @Test
    void aRequiringItemStillPendingIsPending() {
        BookingRequest r = request(BookingRequestStatus.IN_PROGRESS, OPERATOR, RECENT, null);
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, false, true))
                .containsExactly(BookingPendingReason.HAS_PENDING_REQUIRED_ITEM);
    }

    @Test
    void partiallyConfirmedIsPending() {
        BookingRequest r = request(BookingRequestStatus.PARTIALLY_CONFIRMED, OPERATOR, RECENT, null);
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, false, false))
                .containsExactly(BookingPendingReason.PARTIALLY_CONFIRMED);
    }

    @Test
    void anOverdueNextActionIsPending() {
        BookingRequest r = request(BookingRequestStatus.IN_PROGRESS, OPERATOR, RECENT, TODAY.minusDays(1));
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, false, false))
                .containsExactly(BookingPendingReason.OVERDUE_NEXT_ACTION);
    }

    @Test
    void aFutureNextActionIsNotPending() {
        BookingRequest r = request(BookingRequestStatus.IN_PROGRESS, OPERATOR, RECENT, TODAY.plusDays(1));
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, false, false)).isEmpty();
    }

    @Test
    void aCleanInProgressRequestHasNoReason() {
        BookingRequest r = request(BookingRequestStatus.IN_PROGRESS, OPERATOR, RECENT, null);
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, false, false)).isEmpty();
    }

    @Test
    void anAttemptExactlyAtTheWindowEdgeIsNotStaleButOneSecondBeforeIs() {
        Instant edge = Instant.parse("2026-06-08T12:00:00Z"); // exactly 7 days before NOW
        assertThat(BookingRequestPendingReasons.of(
                        request(BookingRequestStatus.IN_PROGRESS, OPERATOR, edge, null), NOW, TODAY, false, false))
                .isEmpty();
        assertThat(BookingRequestPendingReasons.of(
                        request(BookingRequestStatus.IN_PROGRESS, OPERATOR, edge.minusSeconds(1), null),
                        NOW,
                        TODAY,
                        false,
                        false))
                .containsExactly(BookingPendingReason.IN_PROGRESS_WITHOUT_RECENT_ATTEMPT);
    }

    @Test
    void severalReasonsAddUp() {
        // Unassigned + pending without attempt + a failed item + a requiring-pending item + overdue next action.
        BookingRequest r = request(BookingRequestStatus.PENDING, null, null, TODAY.minusDays(2));
        assertThat(BookingRequestPendingReasons.of(r, NOW, TODAY, true, true))
                .containsExactlyInAnyOrder(
                        BookingPendingReason.UNASSIGNED_OPERATOR,
                        BookingPendingReason.PENDING_WITHOUT_ATTEMPT,
                        BookingPendingReason.HAS_FAILED_ITEM,
                        BookingPendingReason.HAS_PENDING_REQUIRED_ITEM,
                        BookingPendingReason.OVERDUE_NEXT_ACTION);
    }
}
