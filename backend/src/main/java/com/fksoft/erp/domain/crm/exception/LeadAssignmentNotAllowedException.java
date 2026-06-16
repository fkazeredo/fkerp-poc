package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when a user without full assignment authority tries to assign a Lead to someone other than
 * themselves (or to unassign it).
 */
public class LeadAssignmentNotAllowedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LeadAssignmentNotAllowedException() {
        super("lead.assignment-not-allowed");
    }
}
