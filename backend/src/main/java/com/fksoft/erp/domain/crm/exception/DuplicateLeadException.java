package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.error.ErrorDetails;
import java.io.Serial;
import java.util.Map;
import java.util.UUID;

/**
 * Raised when a new Lead duplicates an open (non-lost) Lead by phone/WhatsApp number or e-mail. The
 * existing Lead id is carried as a detail so the caller can open the original instead of recreating it.
 */
public class DuplicateLeadException extends DomainException implements ErrorDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient UUID existingLeadId;

    public DuplicateLeadException(UUID existingLeadId) {
        super("lead.duplicate");
        this.existingLeadId = existingLeadId;
    }

    @Override
    public Map<String, String> details() {
        return Map.of("leadId", existingLeadId.toString());
    }
}
