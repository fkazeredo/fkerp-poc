package com.fksoft.erp.domain.booking.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reference data: type of a manual booking attempt (managed cadastro). */
@Entity
@Table(name = "booking_attempt_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingAttemptType extends ReferenceData {

    /**
     * Creates a new active BookingAttemptType.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new BookingAttemptType
     */
    public static BookingAttemptType create(String code, String label, int sortOrder) {
        BookingAttemptType value = new BookingAttemptType();
        value.init(code, label, sortOrder);
        return value;
    }
}
