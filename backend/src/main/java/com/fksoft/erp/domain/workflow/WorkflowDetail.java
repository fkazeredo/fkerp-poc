package com.fksoft.erp.domain.workflow;

import java.util.List;
import java.util.UUID;

/**
 * Admin read model of a configurable workflow: its states, transitions and attention rules — what the visual
 * editor renders (states = nodes, transitions = edges) and what the rule panel edits. Exposes the
 * {@code system} flags so the UI can lock the protected (seeded) rows.
 *
 * @param code the workflow definition code
 * @param label the display label
 * @param states the states (nodes), in order
 * @param transitions the transitions (edges), in order
 * @param attentionRules the configurable pending-items worklist rules, in order
 */
public record WorkflowDetail(
        String code,
        String label,
        List<StateView> states,
        List<TransitionView> transitions,
        List<AttentionRuleView> attentionRules) {

    /** A workflow state (a diagram node). */
    public record StateView(
            UUID id, String code, String label, String category, int sortOrder, boolean active, boolean system) {
        static StateView from(WorkflowState s) {
            return new StateView(
                    s.id(), s.code(), s.label(), s.category().name(), s.sortOrder(), s.active(), s.system());
        }
    }

    /** A workflow transition (a diagram edge), with its rule keys. */
    public record TransitionView(
            UUID id,
            String code,
            String label,
            String fromState,
            String toState,
            String trigger,
            boolean system,
            List<RuleView> rules) {
        static TransitionView from(WorkflowTransition t) {
            return new TransitionView(
                    t.id(),
                    t.code(),
                    t.label(),
                    t.fromState().code(),
                    t.toState().code(),
                    t.trigger().name(),
                    t.system(),
                    t.rules().stream().map(RuleView::from).toList());
        }
    }

    /** A transition rule (a catalog guard/validator/post-function attached to a transition). */
    public record RuleView(UUID id, String kind, String ruleKey, String params, int sortOrder, boolean system) {
        static RuleView from(WorkflowRule r) {
            return new RuleView(r.id(), r.kind().name(), r.ruleKey(), r.params(), r.sortOrder(), r.system());
        }
    }

    /** A configurable attention rule (a pending-items worklist reason). */
    public record AttentionRuleView(
            UUID id,
            String conditionKey,
            Integer thresholdDays,
            String stateValue,
            String code,
            String label,
            int sortOrder,
            boolean active,
            boolean system) {
        static AttentionRuleView from(WorkflowAttentionRule r) {
            return new AttentionRuleView(
                    r.id(),
                    r.conditionKey(),
                    r.thresholdDays(),
                    r.stateValue(),
                    r.code(),
                    r.label(),
                    r.sortOrder(),
                    r.active(),
                    r.system());
        }
    }

    /**
     * Assembles the detail from the definition and its loaded states / transitions / attention rules.
     *
     * @param definition the workflow definition
     * @param states the definition's states (ordered)
     * @param transitions the definition's transitions (ordered)
     * @param attentionRules the definition's attention rules (ordered)
     * @return the admin detail read model
     */
    public static WorkflowDetail from(
            WorkflowDefinition definition,
            List<WorkflowState> states,
            List<WorkflowTransition> transitions,
            List<WorkflowAttentionRule> attentionRules) {
        return new WorkflowDetail(
                definition.code(),
                definition.label(),
                states.stream().map(StateView::from).toList(),
                transitions.stream().map(TransitionView::from).toList(),
                attentionRules.stream().map(AttentionRuleView::from).toList());
    }
}
