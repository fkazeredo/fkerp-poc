package com.fksoft.erp.domain.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The manual failure recorded on a {@link BookingItem} (Sprint 4): when the operator determines a booking item
 * could not be reserved, they record the reason, an optional note, and who/when. It is an {@code @Embeddable}
 * value object on the item, populated only when the item is failed (the current/last failure — the detailed
 * try-by-try log lives in the attempt history). A failed item stays visible as an operational problem; it may
 * receive new attempts and be retried and later confirmed. Recording a failure carries <b>no monetary data</b>,
 * never cancels the Commercial Order, and creates no Financial/Commission/Customer Care record.
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BookingItemFailure {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "failure_reason_id")
    private BookingFailureReason failureReason;

    @Column(name = "failure_note")
    private String failureNote;

    @Column(name = "failure_by")
    private UUID failedBy;

    @Column(name = "failure_at")
    private Instant failedAt;
}
