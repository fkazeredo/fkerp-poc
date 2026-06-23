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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A state in a {@link WorkflowDefinition} (the data-driven replacement for an enum constant of a status
 * enum). Carries a stable {@code code} the application binds to, a display {@code label}, a structural
 * {@link WorkflowStateCategory} (used by queries instead of the old {@code isTerminal()}/{@code isOpen()}
 * helpers), a sort order, an {@code active} flag and a {@code system} flag. A {@code system} state was
 * seeded from the original enum and is protected: its label/order may be edited but its {@code code} is
 * immutable and it cannot be deleted (the application depends on it).
 */
@Entity
@Table(name = "workflow_states", uniqueConstraints = @UniqueConstraint(columnNames = {"definition_id", "code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowState {

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

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false)
    private String label;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkflowStateCategory category;

    @PositiveOrZero
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean system;

    /**
     * Creates a new state of a definition.
     *
     * @param definition the owning definition
     * @param code stable, immutable code
     * @param label display label
     * @param category structural category
     * @param sortOrder sort order
     * @return the new (custom, non-system) state
     */
    public static WorkflowState of(
            WorkflowDefinition definition, String code, String label, WorkflowStateCategory category, int sortOrder) {
        WorkflowState state = new WorkflowState();
        state.id = UUID.randomUUID();
        state.definition = definition;
        state.code = code;
        state.label = label;
        state.category = category;
        state.sortOrder = sortOrder;
        state.active = true;
        state.system = false;
        return state;
    }

    /**
     * Whether this is a closed (terminal) state, derived from its category.
     *
     * @return {@code true} for the terminal categories
     */
    public boolean terminal() {
        return category.isTerminal();
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }

    public void recategorize(WorkflowStateCategory newCategory) {
        this.category = newCategory;
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
