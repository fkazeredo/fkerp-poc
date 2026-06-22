package com.fksoft.erp.domain.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Executes a configured workflow transition: the data-driven heart of the reform. Given a definition, the
 * record's current state and a requested transition code, it resolves the transition from the database
 * graph, runs the transition's guards (conditions + validators) — which throw to block — and then its post
 * functions (side effects), returning the destination {@link WorkflowState} for the caller to assign onto
 * the aggregate. The reduction algorithms of system transitions (e.g. a booking consolidation) stay in
 * code as post functions; only the states and the graph are data.
 *
 * <p>The engine runs inside the calling application service's transaction (it is not annotated
 * {@code @Transactional} itself), so lazy associations and post-function mutations participate in it.
 */
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WorkflowTransitionRepository transitions;
    private final WorkflowStepRegistry registry;

    /**
     * Applies the named transition from {@code current}, running its guards then its post functions.
     *
     * @param definitionCode the workflow definition code (the lifecycle key)
     * @param current the record's current state
     * @param transitionCode the requested transition code
     * @param ctx the transition context (subject, actor, payload)
     * @return the destination state to assign onto the aggregate
     * @throws WorkflowTransitionNotAllowedException if the transition is unknown or its source state is not
     *     the current state
     */
    public WorkflowState apply(
            String definitionCode, WorkflowState current, String transitionCode, WorkflowContext ctx) {
        if (current == null) {
            throw new WorkflowTransitionNotAllowedException();
        }
        WorkflowTransition transition = transitions
                .findByDefinition_CodeAndFromState_CodeAndCode(definitionCode, current.code(), transitionCode)
                .orElseThrow(WorkflowTransitionNotAllowedException::new);
        ctx.bindTransition(transition);
        for (WorkflowRule rule : transition.rules()) {
            if (rule.kind().isGuard()) {
                registry.guard(rule.ruleKey()).check(ctx);
            }
        }
        for (WorkflowRule rule : transition.rules()) {
            if (rule.kind() == WorkflowRuleKind.POST_FUNCTION) {
                registry.postFunction(rule.ruleKey()).apply(ctx);
            }
        }
        return transition.toState();
    }
}
