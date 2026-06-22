package com.fksoft.erp.domain.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link WorkflowEngine} orchestration: it resolves the transition from the graph, runs
 * the guards (which block on failure) before the post functions, and rejects transitions that are unknown
 * or whose source state is not the record's current state. The repositories and the step catalog are mocked
 * (no infrastructure needed).
 */
class WorkflowEngineTest {

    private static final String DEF = "opportunity";

    private final WorkflowTransitionRepository transitions = mock(WorkflowTransitionRepository.class);
    private final WorkflowStepRegistry registry = mock(WorkflowStepRegistry.class);
    private final WorkflowEngine engine = new WorkflowEngine(transitions, registry);

    private WorkflowDefinition definition;
    private WorkflowState newState;
    private WorkflowState discovery;
    private WorkflowTransition advance;

    @BeforeEach
    void setUp() {
        definition = WorkflowDefinition.of(DEF, "Oportunidade");
        newState = WorkflowState.of(definition, "NEW_OPPORTUNITY", "Novo", WorkflowStateCategory.INITIAL, 1);
        discovery = WorkflowState.of(definition, "DISCOVERY", "Descoberta", WorkflowStateCategory.ACTIVE, 2);
        advance =
                WorkflowTransition.of(definition, "advance", newState, discovery, "Avançar", TransitionTrigger.USER, 1);
    }

    private WorkflowContext ctx() {
        return WorkflowContext.of(new Object(), UUID.randomUUID());
    }

    @Test
    void runsGuardsThenPostFunctionsAndReturnsDestination() {
        advance.addRule(WorkflowRule.of(advance, WorkflowRuleKind.VALIDATOR, "guard.key", null, 1));
        advance.addRule(WorkflowRule.of(advance, WorkflowRuleKind.POST_FUNCTION, "post.key", null, 2));
        WorkflowGuard guard = mock(WorkflowGuard.class);
        WorkflowPostFunction post = mock(WorkflowPostFunction.class);
        when(transitions.findByDefinition_CodeAndCode(DEF, "advance")).thenReturn(Optional.of(advance));
        when(registry.guard("guard.key")).thenReturn(guard);
        when(registry.postFunction("post.key")).thenReturn(post);

        WorkflowState result = engine.apply(DEF, newState, "advance", ctx());

        assertThat(result).isEqualTo(discovery);
        verify(guard).check(any());
        verify(post).apply(any());
    }

    @Test
    void rejectsUnknownTransition() {
        when(transitions.findByDefinition_CodeAndCode(DEF, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.apply(DEF, newState, "nope", ctx()))
                .isInstanceOf(WorkflowTransitionNotAllowedException.class);
    }

    @Test
    void rejectsTransitionFiredFromTheWrongState() {
        when(transitions.findByDefinition_CodeAndCode(DEF, "advance")).thenReturn(Optional.of(advance));

        // advance.fromState is NEW_OPPORTUNITY, but the record is already in DISCOVERY
        assertThatThrownBy(() -> engine.apply(DEF, discovery, "advance", ctx()))
                .isInstanceOf(WorkflowTransitionNotAllowedException.class);
    }

    @Test
    void guardFailureBlocksTheTransitionAndSkipsPostFunctions() {
        advance.addRule(WorkflowRule.of(advance, WorkflowRuleKind.CONDITION, "guard.key", null, 1));
        advance.addRule(WorkflowRule.of(advance, WorkflowRuleKind.POST_FUNCTION, "post.key", null, 2));
        WorkflowGuard guard = mock(WorkflowGuard.class);
        WorkflowPostFunction post = mock(WorkflowPostFunction.class);
        doThrow(new IllegalStateException("blocked")).when(guard).check(any());
        when(transitions.findByDefinition_CodeAndCode(DEF, "advance")).thenReturn(Optional.of(advance));
        when(registry.guard("guard.key")).thenReturn(guard);

        assertThatThrownBy(() -> engine.apply(DEF, newState, "advance", ctx()))
                .isInstanceOf(IllegalStateException.class);
        verify(post, never()).apply(any());
    }
}
