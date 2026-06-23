package com.fksoft.erp.domain.workflow;

import java.util.List;
import java.util.Map;

/**
 * The catalog of attention-condition keys available per workflow definition (the detection logic the
 * {@link WorkflowAttentionRule}s reference), with the typed parameters each condition uses. It is the single
 * source of truth the authoring UI lists and the admin service validates a rule's {@code conditionKey}
 * against. The condition logic itself lives in the owning domain's {@code *PendingReasons} /
 * {@code *PendingSpecifications}; this catalog only declares the contract.
 */
public final class WorkflowAttentionConditionCatalog {

    /**
     * A catalog condition: its key plus whether it uses the {@code thresholdDays} / {@code stateValue}
     * parameters.
     *
     * @param conditionKey the condition key
     * @param usesDays whether the condition uses the staleness-window threshold (days)
     * @param usesState whether the condition uses a state/status value
     */
    public record ConditionDescriptor(String conditionKey, boolean usesDays, boolean usesState) {}

    private static final Map<String, List<ConditionDescriptor>> BY_WORKFLOW = Map.of(
            "lead",
                    List.of(
                            new ConditionDescriptor("UNASSIGNED", false, false),
                            new ConditionDescriptor("NEW_WITHOUT_INTERACTION", false, false),
                            new ConditionDescriptor("OVERDUE_NEXT_CONTACT", false, false),
                            new ConditionDescriptor("CONTACTED_WITHOUT_OUTCOME", false, false)),
            "opportunity",
                    List.of(
                            new ConditionDescriptor("NO_RECENT_ACTIVITY", true, false),
                            new ConditionDescriptor("NEXT_ACTION_OVERDUE", false, false),
                            new ConditionDescriptor("IN_STATE_LONGER_THAN", true, true),
                            new ConditionDescriptor("IN_STATE", false, true),
                            new ConditionDescriptor("EXPECTED_CLOSE_OVERDUE", false, false)),
            "booking_request",
                    List.of(
                            new ConditionDescriptor("UNASSIGNED_OPERATOR", false, false),
                            new ConditionDescriptor("STATUS_IS", false, true),
                            new ConditionDescriptor("IN_PROGRESS_STALE", true, false),
                            new ConditionDescriptor("HAS_FAILED_ITEM", false, false),
                            new ConditionDescriptor("HAS_PENDING_REQUIRED_ITEM", false, false),
                            new ConditionDescriptor("NEXT_ACTION_OVERDUE", false, false)));

    private WorkflowAttentionConditionCatalog() {}

    /**
     * The condition descriptors available for a workflow definition.
     *
     * @param workflowCode the definition code
     * @return the descriptors (empty for an unknown definition)
     */
    public static List<ConditionDescriptor> forWorkflow(String workflowCode) {
        return BY_WORKFLOW.getOrDefault(workflowCode, List.of());
    }

    /**
     * Whether a condition key is valid for a workflow definition.
     *
     * @param workflowCode the definition code
     * @param conditionKey the condition key
     * @return {@code true} when the condition is in the workflow's catalog
     */
    public static boolean supports(String workflowCode, String conditionKey) {
        return forWorkflow(workflowCode).stream().anyMatch(d -> d.conditionKey().equals(conditionKey));
    }

    /**
     * The whole catalog, keyed by workflow definition code (for the authoring UI).
     *
     * @return the catalog
     */
    public static Map<String, List<ConditionDescriptor>> all() {
        return BY_WORKFLOW;
    }
}
