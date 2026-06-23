package com.fksoft.erp.domain.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One configurable rule attached to a {@link WorkflowTransition}: a condition, validator or post-function
 * (its {@link WorkflowRuleKind}) identified by a {@code ruleKey} resolved against the engine catalog
 * (a registered {@link WorkflowGuard} or {@link WorkflowPostFunction} bean), with optional JSON
 * {@code params} and an execution order. A {@code system} rule was seeded from the original flow and is
 * protected from deletion.
 */
@Entity
@Table(name = "workflow_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowRule {

    @Id
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transition_id", nullable = false)
    private WorkflowTransition transition;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowRuleKind kind;

    @NotBlank
    @Size(max = 100)
    @Column(name = "rule_key", nullable = false)
    private String ruleKey;

    @Size(max = 2000)
    @Column(name = "params", length = 2000)
    private String params;

    @PositiveOrZero
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean system;

    /**
     * Creates a new rule on a transition.
     *
     * @param transition the owning transition
     * @param kind condition / validator / post-function
     * @param ruleKey the catalog key of the implementing bean
     * @param params optional JSON parameters (or {@code null})
     * @param sortOrder execution order within its phase
     * @return the new (custom, non-system) rule
     */
    public static WorkflowRule of(
            WorkflowTransition transition, WorkflowRuleKind kind, String ruleKey, String params, int sortOrder) {
        WorkflowRule rule = new WorkflowRule();
        rule.id = UUID.randomUUID();
        rule.transition = transition;
        rule.kind = kind;
        rule.ruleKey = ruleKey;
        rule.params = params;
        rule.sortOrder = sortOrder;
        rule.system = false;
        return rule;
    }
}
