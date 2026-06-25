package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.error.ErrorDetails;
import java.io.Serial;
import java.util.Map;
import java.util.UUID;

/** Raised when a Commercial Order already has an active (non-rejected/non-cancelled) Commission. */
public class CommissionAlreadyExistsException extends DomainException implements ErrorDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient UUID existingCommissionId;

    public CommissionAlreadyExistsException(UUID existingCommissionId) {
        super("commission.already-exists");
        this.existingCommissionId = existingCommissionId;
    }

    @Override
    public Map<String, String> details() {
        return Map.of("commissionId", existingCommissionId.toString());
    }
}
