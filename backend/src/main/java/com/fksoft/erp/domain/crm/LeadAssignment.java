package com.fksoft.erp.domain.crm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An assignment-history entry of a {@link Lead} (part of the Lead aggregate): who set which
 * responsible person, from whom, and when. Preserves the commercial history of ownership changes.
 */
@Entity
@Table(name = "lead_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeadAssignment {

    @Id
    private UUID id;

    @Column(name = "from_responsible_id")
    private UUID fromResponsibleId;

    @Column(name = "to_responsible_id")
    private UUID toResponsibleId;

    @NotNull
    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @NotNull
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    static LeadAssignment of(UUID fromResponsibleId, UUID toResponsibleId, UUID assignedBy) {
        LeadAssignment assignment = new LeadAssignment();
        assignment.id = UUID.randomUUID();
        assignment.fromResponsibleId = fromResponsibleId;
        assignment.toResponsibleId = toResponsibleId;
        assignment.assignedBy = assignedBy;
        assignment.assignedAt = Instant.now();
        return assignment;
    }
}
