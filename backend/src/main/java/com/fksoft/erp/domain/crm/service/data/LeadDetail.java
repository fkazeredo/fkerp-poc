package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadAssignment;
import com.fksoft.erp.domain.crm.model.LeadInteraction;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full Lead detail (read model). {@code qualification} and {@code loss} are present only when the lead
 * has been qualified / lost; the history lists are empty when there is none. Assembled from the Lead
 * aggregate plus a map of user id → display name (resolved from Identity).
 */
public record LeadDetail(
        UUID id,
        String name,
        String phone,
        String whatsapp,
        String email,
        String origin,
        String status,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        Instant createdAt,
        Instant updatedAt,
        Instant nextContactAt,
        List<InteractionItem> interactions,
        List<AssignmentItem> assignments,
        QualificationInfo qualification,
        LossInfo loss) {

    /**
     * Assembles the detail from the Lead aggregate and the resolved user names.
     *
     * @param lead the lead aggregate (with its interactions/assignments loaded)
     * @param names map of user id → display name for every actor referenced by the lead
     * @return the detail read model
     */
    public static LeadDetail from(Lead lead, Map<UUID, String> names) {
        List<InteractionItem> interactions = lead.interactions().stream()
                .sorted(Comparator.comparing(LeadInteraction::occurredAt).reversed())
                .map(i -> new InteractionItem(
                        i.id(),
                        i.type().label(),
                        i.result() != null ? i.result().label() : null,
                        i.content(),
                        i.occurredAt(),
                        i.nextContactAt(),
                        names.get(i.registeredBy())))
                .toList();
        List<AssignmentItem> assignments = lead.assignments().stream()
                .sorted(Comparator.comparing(LeadAssignment::assignedAt).reversed())
                .map(a -> new AssignmentItem(
                        nameOf(names, a.fromResponsibleId()),
                        nameOf(names, a.toResponsibleId()),
                        names.get(a.assignedBy()),
                        a.assignedAt()))
                .toList();
        QualificationInfo qualification = lead.qualifiedAt() == null
                ? null
                : new QualificationInfo(
                        lead.qualifiedAt(),
                        names.get(lead.qualifiedBy()),
                        lead.mainInterest(),
                        lead.qualificationNote());
        LossInfo loss = lead.lostAt() == null
                ? null
                : new LossInfo(
                        lead.lossReason() != null ? lead.lossReason().label() : null,
                        lead.lostAt(),
                        names.get(lead.lostBy()),
                        lead.lossNote());

        return new LeadDetail(
                lead.id(),
                lead.name(),
                lead.phone(),
                lead.whatsapp(),
                lead.email(),
                lead.origin().label(),
                lead.status(),
                lead.responsiblePersonId(),
                nameOf(names, lead.responsiblePersonId()),
                lead.responsiblePersonId() == null,
                lead.createdAt(),
                lead.updatedAt(),
                lead.nextContactAt(),
                interactions,
                assignments,
                qualification,
                loss);
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    /** A single interaction in the lead history. */
    public record InteractionItem(
            UUID id,
            String type,
            String result,
            String content,
            Instant occurredAt,
            Instant nextContactAt,
            String registeredBy) {}

    /** A single assignment-history entry. */
    public record AssignmentItem(String from, String to, String by, Instant at) {}

    /** Qualification outcome. */
    public record QualificationInfo(Instant qualifiedAt, String qualifiedBy, String mainInterest, String note) {}

    /** Loss outcome. */
    public record LossInfo(String reason, Instant lostAt, String lostBy, String note) {}
}
