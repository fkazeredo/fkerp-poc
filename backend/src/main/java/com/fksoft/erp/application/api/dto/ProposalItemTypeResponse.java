package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.sales.model.ProposalItemType;
import java.util.UUID;

/**
 * Proposal item type cadastro value response.
 *
 * @param id the id
 * @param code the stable code
 * @param label the display label
 * @param requiresBooking whether items of this type require a booking operation
 * @param active whether the value is active
 * @param sortOrder the sort order
 */
public record ProposalItemTypeResponse(
        UUID id, String code, String label, boolean requiresBooking, boolean active, int sortOrder) {

    public static ProposalItemTypeResponse from(ProposalItemType value) {
        return new ProposalItemTypeResponse(
                value.id(), value.code(), value.label(), value.requiresBooking(), value.active(), value.sortOrder());
    }
}
