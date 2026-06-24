package com.fksoft.erp.domain.financial.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reference data: how a Receivable payment was received (Cash, Bank transfer, Pix, Credit/Debit card, Invoice
 * payment, Other). It is a managed cadastro — an admin can add/rename/deactivate values without code, and no
 * logic branches on the specific value (it is a label on a Payment). Inactive values cannot be used for new
 * payments but remain for historical integrity.
 */
@Entity
@Table(name = "payment_methods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethod extends ReferenceData {

    /**
     * Creates a new active PaymentMethod.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new PaymentMethod
     */
    public static PaymentMethod create(String code, String label, int sortOrder) {
        PaymentMethod value = new PaymentMethod();
        value.init(code, label, sortOrder);
        return value;
    }
}
