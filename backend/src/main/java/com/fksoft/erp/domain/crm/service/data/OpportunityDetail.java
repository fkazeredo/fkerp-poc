package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.model.OpportunityStageChange;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full Opportunity detail (read model), assembled from the Opportunity aggregate plus its source Lead
 * (for traceability) and a map of user id → display name. Exposes commercial pipeline data only — never
 * Proposal, Sale, Sales Order, Booking, Financial, Commission or Customer Care data.
 *
 * <p>{@code loss} is present only when the Opportunity is LOST. {@code stageHistory} is the pipeline
 * movement history (newest first), empty until the first transition. {@code activities} and
 * {@code nextActionDate} stay reserved (empty/{@code null}) for the future Opportunity-activities slice.
 *
 * @param sourceLead the source Lead (kept traceable; still the system of record for contacts/history)
 * @param loss the loss outcome when LOST, else {@code null}
 * @param stageHistory the pipeline stage-movement history (newest first; empty when never moved)
 * @param activities reserved (always empty for now) — future Opportunity-activities slice
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
        List<StageChange> stageHistory,
        List<Object> activities,
        LocalDate nextActionDate) {

    /**
     * Assembles the detail from the Opportunity, its source Lead and the resolved user names.
     *
     * @param o the opportunity aggregate (with its stage changes loaded)
     * @param lead the source Lead (loaded for traceability)
     * @param names map of user id → display name for every actor referenced by the Opportunity
     * @return the detail read model
     */
    public static OpportunityDetail from(Opportunity o, Lead lead, Map<UUID, String> names) {
        SourceLead sourceLead =
                new SourceLead(lead.id(), lead.name(), lead.phone(), lead.whatsapp(), lead.email(), lead.status());
        LossInfo loss = o.lostAt() == null
                ? null
                : new LossInfo(
                        o.lossReason() != null ? o.lossReason().label() : null,
                        o.lostAt(),
                        nameOf(names, o.lostBy()),
                        o.lossNote());
        List<StageChange> stageHistory = o.stageChanges().stream()
                .sorted(Comparator.comparing(OpportunityStageChange::changedAt).reversed())
                .map(c -> new StageChange(c.fromStage(), c.toStage(), c.changedAt(), nameOf(names, c.changedBy())))
                .toList();
        return new OpportunityDetail(
                o.id(),
                o.leadId(),
                o.name(),
                o.stage(),
                o.responsiblePersonId(),
                nameOf(names, o.responsiblePersonId()),
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
                stageHistory,
                List.of(),
                null);
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    /** The source Lead, kept traceable from the Opportunity. */
    public record SourceLead(UUID id, String name, String phone, String whatsapp, String email, LeadStatus status) {}

    /** Loss outcome (present only when the Opportunity is LOST). */
    public record LossInfo(String reason, Instant lostAt, String lostBy, String note) {}

    /** A single pipeline stage-movement entry. */
    public record StageChange(OpportunityStage from, OpportunityStage to, Instant at, String by) {}
}
