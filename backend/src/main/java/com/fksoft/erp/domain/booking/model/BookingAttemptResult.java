package com.fksoft.erp.domain.booking.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reference data: outcome of a manual booking attempt (managed cadastro). */
@Entity
@Table(name = "booking_attempt_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingAttemptResult extends ReferenceData {

    /**
     * Creates a new active BookingAttemptResult.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new BookingAttemptResult
     */
    public static BookingAttemptResult create(String code, String label, int sortOrder) {
        BookingAttemptResult value = new BookingAttemptResult();
        value.init(code, label, sortOrder);
        return value;
    }
}
