package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.LeadStatus;
import com.fksoft.erp.domain.crm.PendingLeadView;
import com.fksoft.erp.domain.crm.PendingReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pending Lead item (entity-free transport DTO) for the operational worklist. {@code reasons} are
 * stable codes the UI localizes; {@code unassigned} flags Leads with no responsible.
 */
public record PendingLeadResponse(
        UUID id,
        String name,
        String mainContact,
        LeadStatus status,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        Instant createdAt,
        Instant nextContactAt,
        List<PendingReason> reasons) {

    /**
     * Maps the domain pending view to the transport DTO.
     *
     * @param v the pending view
     * @return the response item
     */
    public static PendingLeadResponse from(PendingLeadView v) {
        return new PendingLeadResponse(
                v.id(),
                v.name(),
                v.mainContact(),
                v.status(),
                v.responsibleId(),
                v.responsibleName(),
                v.unassigned(),
                v.createdAt(),
                v.nextContactAt(),
                v.reasons());
    }
}
