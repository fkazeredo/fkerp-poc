package com.fksoft.erp.domain.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.erp.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the seeded Lead workflow graph (V40): the data-driven replacement for the LeadStatus enum and its
 * transitions. Asserts the states, their categories, the transition graph (including the multi-source "lose"
 * and the system "contact"), and the require-responsible validator on "qualify" — all as protected system rows.
 */
@Transactional
class WorkflowSeedIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WorkflowDefinitionRepository definitions;

    @Autowired
    private WorkflowStateRepository states;

    @Autowired
    private WorkflowTransitionRepository transitions;

    @Test
    void seedsTheLeadWorkflowStatesAsSystemRows() {
        assertThat(definitions.findByCode("lead")).isPresent();

        List<WorkflowState> leadStates = states.findByDefinition_CodeOrderBySortOrderAsc("lead");
        assertThat(leadStates).extracting(WorkflowState::code).containsExactly("NEW", "CONTACTED", "QUALIFIED", "LOST");
        assertThat(leadStates).allMatch(WorkflowState::system).allMatch(WorkflowState::active);

        WorkflowState lost = leadStates.stream()
                .filter(s -> s.code().equals("LOST"))
                .findFirst()
                .orElseThrow();
        assertThat(lost.category()).isEqualTo(WorkflowStateCategory.TERMINAL_NEGATIVE);
        assertThat(lost.terminal()).isTrue();
        WorkflowState qualified = leadStates.stream()
                .filter(s -> s.code().equals("QUALIFIED"))
                .findFirst()
                .orElseThrow();
        assertThat(qualified.category()).isEqualTo(WorkflowStateCategory.ACTIVE);
        assertThat(qualified.terminal()).isFalse();
    }

    @Test
    void seedsTheLeadTransitionGraphIncludingMultiSourceLose() {
        assertThat(transitions.findByDefinition_CodeAndFromState_CodeAndCode("lead", "NEW", "contact"))
                .isPresent();
        assertThat(transitions.findByDefinition_CodeAndFromState_CodeAndCode("lead", "CONTACTED", "qualify"))
                .isPresent();
        // "lose" is available from every non-terminal state, and never from LOST
        assertThat(transitions.findByDefinition_CodeAndFromState_CodeAndCode("lead", "NEW", "lose"))
                .isPresent();
        assertThat(transitions.findByDefinition_CodeAndFromState_CodeAndCode("lead", "CONTACTED", "lose"))
                .isPresent();
        assertThat(transitions.findByDefinition_CodeAndFromState_CodeAndCode("lead", "QUALIFIED", "lose"))
                .isPresent();
        assertThat(transitions.findByDefinition_CodeAndFromState_CodeAndCode("lead", "LOST", "lose"))
                .isEmpty();
    }

    @Test
    void seedsTheRequireResponsibleValidatorOnQualify() {
        WorkflowTransition qualify = transitions
                .findByDefinition_CodeAndFromState_CodeAndCode("lead", "CONTACTED", "qualify")
                .orElseThrow();
        assertThat(qualify.trigger()).isEqualTo(TransitionTrigger.USER);
        assertThat(qualify.rules())
                .extracting(WorkflowRule::kind, WorkflowRule::ruleKey)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(WorkflowRuleKind.VALIDATOR, "lead.require-responsible"));

        WorkflowTransition contact = transitions
                .findByDefinition_CodeAndFromState_CodeAndCode("lead", "NEW", "contact")
                .orElseThrow();
        assertThat(contact.trigger()).isEqualTo(TransitionTrigger.SYSTEM);
    }

    @Test
    void seedsTheOpportunityFunnelWithMultiSourceLoseAndSystemWin() {
        List<WorkflowState> stages = states.findByDefinition_CodeOrderBySortOrderAsc("opportunity");
        assertThat(stages)
                .extracting(WorkflowState::code)
                .containsExactly("NEW_OPPORTUNITY", "DISCOVERY", "PRODUCT_FIT", "READY_FOR_PROPOSAL", "WON", "LOST");
        WorkflowState won =
                stages.stream().filter(s -> s.code().equals("WON")).findFirst().orElseThrow();
        assertThat(won.category()).isEqualTo(WorkflowStateCategory.TERMINAL_POSITIVE);

        // strict forward funnel: a single "advance" edge out of each active stage, none from READY_FOR_PROPOSAL
        WorkflowTransition advanceFromNew = transitions
                .findByDefinition_CodeAndFromState_CodeAndCode("opportunity", "NEW_OPPORTUNITY", "advance")
                .orElseThrow();
        assertThat(advanceFromNew.toState().code()).isEqualTo("DISCOVERY");
        assertThat(transitions.findByDefinition_CodeAndFromState_CodeAndCode(
                        "opportunity", "READY_FOR_PROPOSAL", "advance"))
                .isEmpty();

        // win is a system transition from each active stage; lose never leaves the terminal LOST
        WorkflowTransition win = transitions
                .findByDefinition_CodeAndFromState_CodeAndCode("opportunity", "DISCOVERY", "win")
                .orElseThrow();
        assertThat(win.trigger()).isEqualTo(TransitionTrigger.SYSTEM);
        assertThat(transitions.findByDefinition_CodeAndFromState_CodeAndCode("opportunity", "LOST", "lose"))
                .isEmpty();
    }
}
