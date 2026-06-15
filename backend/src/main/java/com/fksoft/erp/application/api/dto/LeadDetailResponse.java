package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.AssignmentView;
import com.fksoft.erp.domain.crm.InteractionView;
import com.fksoft.erp.domain.crm.LeadDetailView;
import com.fksoft.erp.domain.crm.LeadStatus;
import com.fksoft.erp.domain.crm.LossView;
import com.fksoft.erp.domain.crm.QualificationView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full Lead detail (entity-free transport DTO). {@code qualification} and {@code loss} are present
 * only when the lead has been qualified / lost; the history lists are empty when there is none.
 */
public record LeadDetailResponse(
        UUID id,
        String name,
        String phone,
        String whatsapp,
        String email,
        String origin,
        LeadStatus status,
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
     * Maps the domain detail view to the transport DTO.
     *
     * @param v the detail view
     * @return the response
     */
    public static LeadDetailResponse from(LeadDetailView v) {
        return new LeadDetailResponse(
                v.id(),
                v.name(),
                v.phone(),
                v.whatsapp(),
                v.email(),
                v.originLabel(),
                v.status(),
                v.responsibleId(),
                v.responsibleName(),
                v.unassigned(),
                v.createdAt(),
                v.updatedAt(),
                v.nextContactAt(),
                v.interactions().stream().map(InteractionItem::from).toList(),
                v.assignments().stream().map(AssignmentItem::from).toList(),
                v.qualification() == null ? null : QualificationInfo.from(v.qualification()),
                v.loss() == null ? null : LossInfo.from(v.loss()));
    }

    /** A single interaction in the lead history. */
    public record InteractionItem(
            UUID id, String type, String result, String content, Instant occurredAt, String registeredBy) {
        static InteractionItem from(InteractionView i) {
            return new InteractionItem(
                    i.id(), i.typeLabel(), i.resultLabel(), i.content(), i.occurredAt(), i.registeredByName());
        }
    }

    /** A single assignment-history entry. */
    public record AssignmentItem(String from, String to, String by, Instant at) {
        static AssignmentItem from(AssignmentView a) {
            return new AssignmentItem(
                    a.fromResponsibleName(), a.toResponsibleName(), a.assignedByName(), a.assignedAt());
        }
    }

    /** Qualification outcome. */
    public record QualificationInfo(Instant qualifiedAt, String qualifiedBy, String note) {
        static QualificationInfo from(QualificationView q) {
            return new QualificationInfo(q.qualifiedAt(), q.qualifiedByName(), q.note());
        }
    }

    /** Loss outcome. */
    public record LossInfo(String reason, Instant lostAt, String lostBy, String note) {
        static LossInfo from(LossView l) {
            return new LossInfo(l.lossReasonLabel(), l.lostAt(), l.lostByName(), l.note());
        }
    }
}
