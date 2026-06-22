package com.fksoft.erp.domain.booking.model;

/**
 * Why a Booking Request appears in the operational pending-items worklist (Booking Operations). Exposed in the
 * API as a stable code; the frontend localizes it (raw enum names are never shown to users). A request may have
 * several reasons. Terminal CONFIRMED / CANCELLED requests are never pending.
 */
public enum BookingPendingReason {
    /** No booking operator is assigned yet — the request has not been picked up. */
    UNASSIGNED_OPERATOR,
    /** Still PENDING with no attempt registered — the reservation work has not started. */
    PENDING_WITHOUT_ATTEMPT,
    /** In progress but with no attempt within the staleness window — the work has gone quiet. */
    IN_PROGRESS_WITHOUT_RECENT_ATTEMPT,
    /** At least one booking item failed and needs an operational decision. */
    HAS_FAILED_ITEM,
    /** At least one item that requires booking is still pending. */
    HAS_PENDING_REQUIRED_ITEM,
    /** Some items confirmed and others not — the reservation is only partially confirmed. */
    PARTIALLY_CONFIRMED,
    /** A planned next action (from the latest attempt) is past due. */
    OVERDUE_NEXT_ACTION
}
