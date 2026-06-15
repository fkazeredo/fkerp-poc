package com.fksoft.erp.domain.crm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full read view of a Lead for the detail screen: core data plus its commercial history
 * (interactions, assignments) and outcomes (qualification, loss) when present.
 *
 * @param qualification the qualification outcome, or {@code null} if not qualified
 * @param loss the loss outcome, or {@code null} if not lost
 */
public record LeadDetailView(
        UUID id,
        String name,
        String phone,
        String whatsapp,
        String email,
        String originLabel,
        LeadStatus status,
        UUID responsibleId,
        String responsibleName,
        Instant createdAt,
        Instant updatedAt,
        Instant nextContactAt,
        List<InteractionView> interactions,
        List<AssignmentView> assignments,
        QualificationView qualification,
        LossView loss) {

    public boolean unassigned() {
        return responsibleId == null;
    }
}
