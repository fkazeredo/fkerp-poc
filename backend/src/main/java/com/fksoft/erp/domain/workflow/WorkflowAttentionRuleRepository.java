package com.fksoft.erp.domain.workflow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

/** Repository for {@link WorkflowAttentionRule}s (the configurable pending-items worklist rules). */
public interface WorkflowAttentionRuleRepository extends ListCrudRepository<WorkflowAttentionRule, UUID> {

    /**
     * The active rules of a workflow definition, in evaluation order — what the worklist evaluates.
     *
     * @param code the workflow definition code (e.g. {@code opportunity})
     * @return the active rules ordered by sort order
     */
    List<WorkflowAttentionRule> findByDefinition_CodeAndActiveTrueOrderBySortOrderAsc(String code);

    /**
     * All rules (active or not) of a workflow definition, in order — for the admin/editor.
     *
     * @param code the workflow definition code
     * @return the rules ordered by sort order
     */
    List<WorkflowAttentionRule> findByDefinition_CodeOrderBySortOrderAsc(String code);
}
