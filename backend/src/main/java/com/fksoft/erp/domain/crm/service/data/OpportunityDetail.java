package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full Opportunity detail (read model), assembled from the Opportunity aggregate plus its source Lead
 * (for traceability) and the resolved responsible / lost-by names. Exposes commercial pipeline data only
 * — never Proposal, Sale, Sales Order, Booking, Financial, Commission or Customer Care data.
 *
 * <p>{@code loss} is present only when the Opportunity is LOST. {@code activities},
 * {@code stageHistory} and {@code nextActionDate} are reserved for the future Opportunity-activities and
 * stage-movement slices: they are empty/{@code null} now (no item schema is committed yet) and the UI
 * renders those sections only when populated.
 *
 * @param sourceLead the source Lead (kept traceable; still the system of record for contacts/history)
 * @param loss the loss outcome when LOST, else {@code null}
 * @param activities reserved (always empty for now) — future Opportunity-activities slice
 * @param stageHistory reserved (always empty for now) — future stage-movement slice
 * @param nextActionDate reserved (always {@code null} for now) — future Opportunity-activities slice
 */
public record OpportunityDetail(
        UUID id,
        UUID leadId,
        String name,
        OpportunityStage stage,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        String origin,
        String mainInterest,
        String productType,
        BigDecimal estimatedValue,
        LocalDate expectedCloseDate,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        SourceLead sourceLead,
        LossInfo loss,
        List<Object> activities,
        List<Object> stageHistory,
        LocalDate nextActionDate) {

    /**
     * Assembles the detail from the Opportunity, its source Lead and the resolved user names.
     *
     * @param o the opportunity aggregate
     * @param lead the source Lead (loaded for traceability)
     * @param responsibleName the responsible's display name, or {@code null} when unassigned/unknown
     * @param lostByName the display name of who marked it lost, or {@code null}
     * @return the detail read model
     */
    public static OpportunityDetail from(Opportunity o, Lead lead, String responsibleName, String lostByName) {
        SourceLead sourceLead =
                new SourceLead(lead.id(), lead.name(), lead.phone(), lead.whatsapp(), lead.email(), lead.status());
        LossInfo loss = o.lostAt() == null
                ? null
                : new LossInfo(
                        o.lossReason() != null ? o.lossReason().label() : null, o.lostAt(), lostByName, o.lossNote());
        return new OpportunityDetail(
                o.id(),
                o.leadId(),
                o.name(),
                o.stage(),
                o.responsiblePersonId(),
                responsibleName,
                o.responsiblePersonId() == null,
                o.origin().label(),
                o.mainInterest(),
                o.productType(),
                o.estimatedValue(),
                o.expectedCloseDate(),
                o.notes(),
                o.createdAt(),
                o.updatedAt(),
                sourceLead,
                loss,
                List.of(),
                List.of(),
                null);
    }

    /** The source Lead, kept traceable from the Opportunity. */
    public record SourceLead(UUID id, String name, String phone, String whatsapp, String email, LeadStatus status) {}

    /** Loss outcome (present only when the Opportunity is LOST). */
    public record LossInfo(String reason, Instant lostAt, String lostBy, String note) {}
}
