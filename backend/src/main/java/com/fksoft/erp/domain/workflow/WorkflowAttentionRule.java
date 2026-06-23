package com.fksoft.erp.domain.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A configurable <em>attention rule</em> attached to a {@link WorkflowDefinition}: it declares why a record of
 * that lifecycle needs operational attention (a pending-items worklist reason). It is the data-driven
 * replacement for the hardcoded pending-reason enums — an administrator can enable/disable rules, tune their
 * thresholds, rename their labels and add new ones from the area's condition catalog.
 *
 * <p>Each rule references a {@code conditionKey} from its area's attention-condition catalog (the detection
 * logic, evaluated read-side both as a worklist query predicate and as an in-memory tag), with the optional
 * typed parameters a condition needs ({@code thresholdDays} for a staleness window, {@code stateValue} for a
 * state/status comparison), a stable {@code code} (the reason tag exposed in the worklist contract) and an
 * editable {@code label}. A {@code system} rule was seeded from the original flow: its {@code code} and
 * {@code conditionKey} are immutable and it cannot be deleted (label/threshold/order/active stay editable).
 */
@Entity
@Table(name = "workflow_attention_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowAttentionRule {

    @Id
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private WorkflowDefinition definition;

    @NotBlank
    @Size(max = 100)
    @Column(name = "condition_key", nullable = false)
    private String conditionKey;

    @PositiveOrZero
    @Column(name = "threshold_days")
    private Integer thresholdDays;

    @Size(max = 60)
    @Column(name = "state_value")
    private String stateValue;

    @NotBlank
    @Size(max = 60)
    @Column(nullable = false)
    private String code;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false)
    private String label;

    @PositiveOrZero
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean system;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Creates a new (custom, non-system) attention rule.
     *
     * @param definition the owning workflow definition
     * @param conditionKey the catalog condition key (detection logic)
     * @param thresholdDays the optional staleness window in days (or {@code null})
     * @param stateValue the optional state/status value the condition compares against (or {@code null})
     * @param code the stable reason code exposed in the worklist
     * @param label the display label
     * @param sortOrder the display/evaluation order
     * @return the new rule
     */
    public static WorkflowAttentionRule of(
            WorkflowDefinition definition,
            String conditionKey,
            Integer thresholdDays,
            String stateValue,
            String code,
            String label,
            int sortOrder) {
        WorkflowAttentionRule rule = new WorkflowAttentionRule();
        rule.id = UUID.randomUUID();
        rule.definition = definition;
        rule.conditionKey = conditionKey;
        rule.thresholdDays = thresholdDays;
        rule.stateValue = stateValue;
        rule.code = code;
        rule.label = label;
        rule.sortOrder = sortOrder;
        rule.active = true;
        rule.system = false;
        return rule;
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }

    public void changeThresholdDays(Integer newThresholdDays) {
        this.thresholdDays = newThresholdDays;
    }

    public void reorder(int newSortOrder) {
        this.sortOrder = newSortOrder;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
