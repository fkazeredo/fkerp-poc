package com.fksoft.erp.domain.crm.workflow;

import com.fksoft.erp.domain.crm.exception.LeadQualificationRequiresResponsibleException;
import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.workflow.WorkflowContext;
import com.fksoft.erp.domain.workflow.WorkflowGuard;
import org.springframework.stereotype.Component;

/**
 * Workflow guard for the Lead {@code qualify} transition: a Lead may only be qualified when it has a
 * responsible person. Registered in the engine catalog under {@link #KEY} (referenced by the seeded
 * validator rule on the qualify transition). Throws the specific domain exception so the API error contract
 * is preserved (the old in-entity check).
 */
@Component
public class RequireResponsibleGuard implements WorkflowGuard {

    /** Catalog key referenced by the workflow rule on the Lead qualify transition. */
    public static final String KEY = "lead.require-responsible";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public void check(WorkflowContext ctx) {
        Lead lead = ctx.subjectAs(Lead.class);
        if (!lead.hasResponsible()) {
            throw new LeadQualificationRequiresResponsibleException();
        }
    }
}
