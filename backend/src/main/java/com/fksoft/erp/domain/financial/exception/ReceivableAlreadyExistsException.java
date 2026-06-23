package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.error.ErrorDetails;
import java.io.Serial;
import java.util.Map;
import java.util.UUID;

/** Raised when a Commercial Order already has an active (non-cancelled) Receivable. */
public class ReceivableAlreadyExistsException extends DomainException implements ErrorDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient UUID existingReceivableId;

    public ReceivableAlreadyExistsException(UUID existingReceivableId) {
        super("financial.receivable.already-exists");
        this.existingReceivableId = existingReceivableId;
    }

    @Override
    public Map<String, String> details() {
        return Map.of("receivableId", existingReceivableId.toString());
    }
}
