package com.fksoft.erp.domain.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A configurable workflow (one per business lifecycle: lead, opportunity, proposal, order,
 * booking-request, booking-item). It is the data-driven replacement for the hardcoded status enums: its
 * {@link WorkflowState}s and {@link WorkflowTransition}s live in the database and can be edited by an
 * administrator. The {@code code} is the stable, immutable identifier the application binds to.
 */
@Entity
@Table(name = "workflow_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowDefinition {

    @Id
    private UUID id;

    @NotBlank
    @Size(max = 60)
    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false)
    private String label;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Creates a new workflow definition.
     *
     * @param code stable, immutable code (the lifecycle key the application binds to)
     * @param label display label
     * @return the new definition
     */
    public static WorkflowDefinition of(String code, String label) {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.id = UUID.randomUUID();
        definition.code = code;
        definition.label = label;
        return definition;
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }
}
