package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.error.ErrorDetails;
import java.io.Serial;
import java.util.Map;
import java.util.UUID;

/**
 * Raised when a Lead already originated an Opportunity (a Lead originates at most one). The existing
 * Opportunity id is carried as a detail so the caller can open it instead of creating another.
 */
public class OpportunityAlreadyExistsForLeadException extends DomainException implements ErrorDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient UUID existingOpportunityId;

    public OpportunityAlreadyExistsForLeadException(UUID existingOpportunityId) {
        super("opportunity.already-exists-for-lead");
        this.existingOpportunityId = existingOpportunityId;
    }

    @Override
    public Map<String, String> details() {
        return Map.of("opportunityId", existingOpportunityId.toString());
    }
}
