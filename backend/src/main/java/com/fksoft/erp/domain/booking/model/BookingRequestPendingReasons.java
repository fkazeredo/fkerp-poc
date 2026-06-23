package com.fksoft.erp.domain.booking.model;

import com.fksoft.erp.domain.booking.service.BookingRequestPendingSpecifications;
import com.fksoft.erp.domain.workflow.WorkflowAttentionRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes why a Booking Request is pending, driven by the configurable {@link WorkflowAttentionRule}s of the
 * {@code booking_request} workflow (the data-driven replacement for the former pending-reason enum). Single
 * source of truth for the reason tags shown in the Booking Operations worklist;
 * {@link BookingRequestPendingSpecifications#pending} mirrors these predicates at the query level so the page
 * contains exactly the requests that have at least one reason. Terminal CONFIRMED / CANCELLED requests are
 * never pending. The item-derived flags ({@code hasFailedItem}, {@code hasPendingRequiredItem}) are passed in
 * (batch-resolved) to avoid a lazy load / N+1.
 */
public final class BookingRequestPendingReasons {

    private BookingRequestPendingReasons() {}

    /**
     * The pending reason codes that currently apply to a Booking Request (empty when it needs no action), one
     * per matching active rule (in rule order).
     *
     * @param r the booking request
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @param hasFailedItem whether the request has at least one failed item (batch-resolved)
     * @param hasPendingRequiredItem whether the request has at least one requiring-booking item still pending
     * @param rules the active attention rules of the {@code booking_request} workflow, in order
     * @return the matching reason codes (a request may have several)
     */
    public static List<String> of(
            BookingRequest r,
            Instant now,
            LocalDate today,
            boolean hasFailedItem,
            boolean hasPendingRequiredItem,
            List<WorkflowAttentionRule> rules) {
        List<String> reasons = new ArrayList<>();
        if ("CONFIRMED".equals(r.status()) || "CANCELLED".equals(r.status())) {
            return reasons;
        }
        for (WorkflowAttentionRule rule : rules) {
            if (matches(rule, r, now, today, hasFailedItem, hasPendingRequiredItem)) {
                reasons.add(rule.code());
            }
        }
        return reasons;
    }

    private static boolean matches(
            WorkflowAttentionRule rule,
            BookingRequest r,
            Instant now,
            LocalDate today,
            boolean hasFailedItem,
            boolean hasPendingRequiredItem) {
        return switch (rule.conditionKey()) {
            case "UNASSIGNED_OPERATOR" -> r.bookingOperatorId() == null;
            case "STATUS_IS" -> rule.stateValue().equals(r.status());
            case "IN_PROGRESS_STALE" -> "IN_PROGRESS".equals(r.status())
                    && (r.lastAttemptAt() == null
                            || r.lastAttemptAt().isBefore(now.minus(days(rule), ChronoUnit.DAYS)));
            case "HAS_FAILED_ITEM" -> hasFailedItem;
            case "HAS_PENDING_REQUIRED_ITEM" -> hasPendingRequiredItem;
            case "NEXT_ACTION_OVERDUE" -> r.nextActionDate() != null
                    && r.nextActionDate().isBefore(today);
            default -> false;
        };
    }

    private static int days(WorkflowAttentionRule rule) {
        return rule.thresholdDays() == null ? 0 : rule.thresholdDays();
    }
}
