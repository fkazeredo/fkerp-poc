package com.fksoft.erp.domain.crm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read view of a pending Lead in the operational worklist, with the reasons it needs action. */
public record PendingLeadView(
        UUID id,
        String name,
        String mainContact,
        LeadStatus status,
        UUID responsibleId,
        String responsibleName,
        Instant createdAt,
        Instant nextContactAt,
        List<PendingReason> reasons) {

    public boolean unassigned() {
        return responsibleId == null;
    }
}
