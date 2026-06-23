package com.fksoft.erp.domain.workflow;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service for workflow administration: serves the configurable workflows (states, transitions,
 * attention rules) to the visual editor and edits them. The seeded {@code system} rows are protected — their
 * {@code code}/{@code conditionKey} are immutable and {@code system} attention rules cannot be deleted (the
 * application binds to them); label/threshold/order/active stay editable, and custom rows are fully editable.
 * Editing an attention rule takes effect immediately on the next worklist query (the engine reads the active
 * rules each time).
 */
@Service
@RequiredArgsConstructor
public class WorkflowAdminService {

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowStateRepository states;
    private final WorkflowTransitionRepository transitions;
    private final WorkflowAttentionRuleRepository attentionRules;
    private final WorkflowStepRegistry stepRegistry;

    /**
     * Lists the configurable workflow definitions.
     *
     * @return the workflow summaries (by code)
     */
    @Transactional(readOnly = true)
    public List<WorkflowSummary> list() {
        return definitions.findAllByOrderByCodeAsc().stream()
                .map(WorkflowSummary::from)
                .toList();
    }

    /**
     * The full detail of a workflow definition (states, transitions, attention rules).
     *
     * @param code the definition code
     * @return the detail
     * @throws WorkflowNotFoundException if the definition does not exist
     */
    @Transactional(readOnly = true)
    public WorkflowDetail detail(String code) {
        WorkflowDefinition definition = definitions.findByCode(code).orElseThrow(WorkflowNotFoundException::new);
        return WorkflowDetail.from(
                definition,
                states.findByDefinition_CodeOrderBySortOrderAsc(code),
                transitions.findByDefinition_CodeOrderBySortOrderAsc(code),
                attentionRules.findByDefinition_CodeOrderBySortOrderAsc(code));
    }

    /**
     * The authoring catalog (attention conditions per workflow + the registered guard / post-function keys).
     *
     * @return the catalog view
     */
    @Transactional(readOnly = true)
    public WorkflowCatalogView catalog() {
        return new WorkflowCatalogView(
                WorkflowAttentionConditionCatalog.all(),
                List.copyOf(stepRegistry.guardKeys()),
                List.copyOf(stepRegistry.postFunctionKeys()));
    }

    /**
     * Creates a custom attention rule on a workflow from a catalog condition.
     *
     * @param workflowCode the definition code
     * @param conditionKey the catalog condition key (must be valid for the workflow)
     * @param thresholdDays the optional staleness window in days
     * @param stateValue the optional state/status value
     * @param code the stable reason code exposed in the worklist
     * @param label the display label
     * @param sortOrder the evaluation/display order
     * @return the new rule id
     * @throws WorkflowNotFoundException if the definition does not exist
     * @throws WorkflowConditionUnknownException if the condition is not in the workflow's catalog
     */
    @Transactional
    public UUID createAttentionRule(
            String workflowCode,
            String conditionKey,
            Integer thresholdDays,
            String stateValue,
            String code,
            String label,
            int sortOrder) {
        WorkflowDefinition definition =
                definitions.findByCode(workflowCode).orElseThrow(WorkflowNotFoundException::new);
        if (!WorkflowAttentionConditionCatalog.supports(workflowCode, conditionKey)) {
            throw new WorkflowConditionUnknownException();
        }
        WorkflowAttentionRule rule =
                WorkflowAttentionRule.of(definition, conditionKey, thresholdDays, stateValue, code, label, sortOrder);
        attentionRules.save(rule);
        return rule.id();
    }

    /**
     * Updates an attention rule's label, threshold, order and active flag (code/condition stay immutable).
     *
     * @param id the rule id
     * @param label the new label
     * @param thresholdDays the new threshold (or {@code null})
     * @param sortOrder the new order
     * @param active the new active flag
     * @throws WorkflowNotFoundException if the rule does not exist
     */
    @Transactional
    public void updateAttentionRule(UUID id, String label, Integer thresholdDays, int sortOrder, boolean active) {
        WorkflowAttentionRule rule = attentionRules.findById(id).orElseThrow(WorkflowNotFoundException::new);
        rule.rename(label);
        rule.changeThresholdDays(thresholdDays);
        rule.reorder(sortOrder);
        if (active) {
            rule.activate();
        } else {
            rule.deactivate();
        }
    }

    /**
     * Deletes a custom attention rule. A {@code system} rule is protected and cannot be deleted (deactivate it
     * instead).
     *
     * @param id the rule id
     * @throws WorkflowNotFoundException if the rule does not exist
     * @throws WorkflowSystemRuleProtectedException if the rule is a protected system rule
     */
    @Transactional
    public void deleteAttentionRule(UUID id) {
        WorkflowAttentionRule rule = attentionRules.findById(id).orElseThrow(WorkflowNotFoundException::new);
        if (rule.system()) {
            throw new WorkflowSystemRuleProtectedException();
        }
        attentionRules.delete(rule);
    }

    /**
     * Updates a state's label, order and active flag (the {@code code} and category stay as seeded for a system
     * state; the application binds to the code).
     *
     * @param id the state id
     * @param label the new label
     * @param sortOrder the new order
     * @param active the new active flag
     * @throws WorkflowNotFoundException if the state does not exist
     */
    @Transactional
    public void updateState(UUID id, String label, int sortOrder, boolean active) {
        WorkflowState state = states.findById(id).orElseThrow(WorkflowNotFoundException::new);
        state.rename(label);
        state.reorder(sortOrder);
        if (active) {
            state.activate();
        } else {
            state.deactivate();
        }
    }
}
