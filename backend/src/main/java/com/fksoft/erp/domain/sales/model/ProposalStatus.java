package com.fksoft.erp.domain.sales.model;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lifecycle status of a commercial {@link Proposal} (Sprint 3). A new Proposal starts at {@link #DRAFT}.
 * The full lifecycle is defined here, but only DRAFT is reached in this slice — the transitions
 * (Ready for Review → Approved → Sent → Accepted/Rejected, plus Expired/Cancelled) are later slices.
 *
 * <p>{@link #REJECTED}, {@link #EXPIRED} and {@link #CANCELLED} are the terminal-negative outcomes: a
 * Proposal in one of those is no longer "open", so the Opportunity may originate a new Proposal (see the
 * one-active-proposal-per-Opportunity rule).
 */
public enum ProposalStatus {
    DRAFT,
    READY_FOR_REVIEW,
    APPROVED,
    SENT,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CANCELLED;

    /**
     * Whether the Proposal is still open (not a terminal-negative outcome). An Opportunity may have at
     * most one open Proposal at a time; once the current one is {@link #REJECTED}, {@link #EXPIRED} or
     * {@link #CANCELLED}, a new Proposal may be created.
     *
     * @return {@code true} unless the status is REJECTED, EXPIRED or CANCELLED
     */
    public boolean isOpen() {
        return this != REJECTED && this != EXPIRED && this != CANCELLED;
    }

    /**
     * The set of open statuses (not a terminal-negative outcome). This is the default operational view of
     * the Proposal list — REJECTED, EXPIRED and CANCELLED are excluded unless explicitly filtered in.
     *
     * @return the open statuses (DRAFT, READY_FOR_REVIEW, APPROVED, SENT, ACCEPTED)
     */
    public static Set<ProposalStatus> openStatuses() {
        return Stream.of(values()).filter(ProposalStatus::isOpen).collect(Collectors.toUnmodifiableSet());
    }
}
