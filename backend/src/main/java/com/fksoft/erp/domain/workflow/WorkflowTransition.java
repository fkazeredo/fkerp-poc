package com.fksoft.erp.domain.workflow;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An allowed move between two {@link WorkflowState}s of a {@link WorkflowDefinition} (the data-driven
 * replacement for the hardcoded {@code canAdvanceTo}/transition methods). Carries a stable {@code code}
 * the application fires it by, the {@code fromState}/{@code toState}, a {@link TransitionTrigger} (user vs
 * system), and an ordered list of {@link WorkflowRule}s (conditions/validators/post-functions) chosen from
 * the engine catalog. A {@code system} transition was seeded from the original flow and is protected.
 */
@Entity
@Table(name = "workflow_transitions", uniqueConstraints = @UniqueConstraint(columnNames = {"definition_id", "code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowTransition {

    @Id
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false, updatable = false)
    private WorkflowDefinition definition;

    @NotBlank
    @Size(max = 60)
    @Column(nullable = false, updatable = false)
    private String code;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_state_id", nullable = false)
    private WorkflowState fromState;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_state_id", nullable = false)
    private WorkflowState toState;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false)
    private String label;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TransitionTrigger trigger;

    @PositiveOrZero
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean system;

    @OneToMany(mappedBy = "transition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<WorkflowRule> rules = new ArrayList<>();

    /**
     * Creates a new transition of a definition.
     *
     * @param definition the owning definition
     * @param code stable, immutable code the application fires it by
     * @param fromState the source state
     * @param toState the destination state
     * @param label display label
     * @param trigger user vs system
     * @param sortOrder sort order
     * @return the new (custom, non-system) transition
     */
    public static WorkflowTransition of(
            WorkflowDefinition definition,
            String code,
            WorkflowState fromState,
            WorkflowState toState,
            String label,
            TransitionTrigger trigger,
            int sortOrder) {
        WorkflowTransition transition = new WorkflowTransition();
        transition.id = UUID.randomUUID();
        transition.definition = definition;
        transition.code = code;
        transition.fromState = fromState;
        transition.toState = toState;
        transition.label = label;
        transition.trigger = trigger;
        transition.sortOrder = sortOrder;
        transition.system = false;
        return transition;
    }

    /**
     * Attaches a rule (condition/validator/post-function) to this transition.
     *
     * @param rule the rule to add
     */
    public void addRule(WorkflowRule rule) {
        rules.add(rule);
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }
}
