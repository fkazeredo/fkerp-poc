package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.error.ErrorDetails;
import java.io.Serial;
import java.util.Map;
import java.util.UUID;

/**
 * Raised when an Opportunity already has an open Proposal (at most one active Proposal per Opportunity).
 * The existing Proposal id is carried as a detail so the caller can open it instead of creating another.
 */
public class ProposalAlreadyExistsForOpportunityException extends DomainException implements ErrorDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient UUID existingProposalId;

    public ProposalAlreadyExistsForOpportunityException(UUID existingProposalId) {
        super("proposal.already-exists-for-opportunity");
        this.existingProposalId = existingProposalId;
    }

    @Override
    public Map<String, String> details() {
        return Map.of("proposalId", existingProposalId.toString());
    }
}
