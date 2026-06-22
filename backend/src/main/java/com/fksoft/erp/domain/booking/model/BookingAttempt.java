package com.fksoft.erp.domain.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A manual booking attempt registered on a {@link BookingRequest} (part of the aggregate): the type, the
 * outcome (result), a description, when it happened, who registered it (the author), an optional link to one
 * booking item (or the whole request when {@code null}) and an optional next action date. It preserves the
 * operational history (append-only) before confirmation or failure. It carries <b>no monetary data</b>, never
 * confirms a booking, never creates Financial or Commission data, and never changes a booking item's status —
 * registering it only ever moves the request from {@code PENDING} to
 * {@code IN_PROGRESS}.
 */
@Entity
@Table(name = "booking_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingAttempt {

    @Id
    private UUID id;

    // Read-only mapping of the owning FK (written by the parent BookingRequest's @OneToMany @JoinColumn).
    @Column(name = "booking_request_id", insertable = false, updatable = false)
    private UUID bookingRequestId;

    // Optional link to a single booking item; null means the attempt concerns the whole request.
    @Column(name = "booking_item_id")
    private UUID bookingItemId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingAttemptType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingAttemptResult result;

    @NotBlank
    @Size(max = 4000)
    @Column(nullable = false)
    private String description;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "next_action_date")
    private LocalDate nextActionDate;

    @NotNull
    @Column(name = "registered_by", nullable = false)
    private UUID registeredBy;

    static BookingAttempt of(
            UUID bookingItemId,
            BookingAttemptType type,
            BookingAttemptResult result,
            String description,
            Instant occurredAt,
            LocalDate nextActionDate,
            UUID registeredBy) {
        BookingAttempt attempt = new BookingAttempt();
        attempt.id = UUID.randomUUID();
        attempt.bookingItemId = bookingItemId;
        attempt.type = type;
        attempt.result = result;
        attempt.description = description;
        attempt.occurredAt = occurredAt;
        attempt.nextActionDate = nextActionDate;
        attempt.registeredBy = registeredBy;
        return attempt;
    }
}
