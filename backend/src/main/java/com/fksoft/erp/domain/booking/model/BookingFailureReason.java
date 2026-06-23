package com.fksoft.erp.domain.booking.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reference data: reason a booking item was manually marked as failed (managed cadastro). */
@Entity
@Table(name = "booking_failure_reasons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingFailureReason extends ReferenceData {

    /**
     * Creates a new active BookingFailureReason.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new BookingFailureReason
     */
    public static BookingFailureReason create(String code, String label, int sortOrder) {
        BookingFailureReason value = new BookingFailureReason();
        value.init(code, label, sortOrder);
        return value;
    }
}
