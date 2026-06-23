package com.fksoft.erp.domain.workflow;

/**
 * Admin list item of a configurable workflow.
 *
 * @param code the definition code
 * @param label the display label
 */
public record WorkflowSummary(String code, String label) {

    static WorkflowSummary from(WorkflowDefinition definition) {
        return new WorkflowSummary(definition.code(), definition.label());
    }
}
