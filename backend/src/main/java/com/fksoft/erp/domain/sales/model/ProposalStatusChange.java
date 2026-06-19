package com.fksoft.erp.domain.sales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A status-change entry of a {@link Proposal} (part of the Proposal aggregate): which status it moved from
 * and to, when, and who moved it. Preserves the commercial history of the Proposal lifecycle — and, as the
 * lifecycle grows, carries the approval / sent / customer-decision facts (who and when) through these
 * transition entries.
 */
@Entity
@Table(name = "proposal_status_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProposalStatusChange {

    @Id
    private UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private ProposalStatus fromStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ProposalStatus toStatus;

    @NotNull
    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @NotNull
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    static ProposalStatusChange of(ProposalStatus fromStatus, ProposalStatus toStatus, UUID changedBy) {
        ProposalStatusChange change = new ProposalStatusChange();
        change.id = UUID.randomUUID();
        change.fromStatus = fromStatus;
        change.toStatus = toStatus;
        change.changedBy = changedBy;
        change.changedAt = Instant.now();
        return change;
    }
}
