package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when an installment schedule for a Receivable is invalid: the installments do not sum to the
 * Receivable's total, or an installment has a negative amount or a missing due date (the latter two are
 * normally caught earlier by Bean Validation; this is the last-resort domain guard).
 */
public class InstallmentScheduleInvalidException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InstallmentScheduleInvalidException() {
        super("financial.receivable.installment-schedule-invalid");
    }
}
