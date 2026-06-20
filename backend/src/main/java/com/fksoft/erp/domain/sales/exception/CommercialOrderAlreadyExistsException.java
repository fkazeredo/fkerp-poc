package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.error.ErrorDetails;
import java.io.Serial;
import java.util.Map;
import java.util.UUID;

/**
 * Raised when a Proposal already has an active Commercial Order (at most one active Order per Proposal). The
 * existing Order id is carried as a detail so the caller can open it instead of creating another.
 */
public class CommercialOrderAlreadyExistsException extends DomainException implements ErrorDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient UUID existingOrderId;

    public CommercialOrderAlreadyExistsException(UUID existingOrderId) {
        super("order.already-exists");
        this.existingOrderId = existingOrderId;
    }

    @Override
    public Map<String, String> details() {
        return Map.of("orderId", existingOrderId.toString());
    }
}
