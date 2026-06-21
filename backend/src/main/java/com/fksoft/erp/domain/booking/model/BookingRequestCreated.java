package com.fksoft.erp.domain.booking.model;

import java.util.UUID;

/**
 * Domain event published when a Booking Request is created from a PENDING_BOOKING Commercial Order. A
 * business fact other modules may react to later — it carries no transport concern.
 *
 * @param bookingRequestId the new Booking Request id
 * @param commercialOrderId the source Commercial Order id
 * @param proposalId the source Proposal id
 * @param opportunityId the source Opportunity id
 * @param leadId the source Lead id
 * @param createdBy id of the user who created the request
 * @param responsiblePersonId the commercial responsible preserved from the Order
 */
public record BookingRequestCreated(
        UUID bookingRequestId,
        UUID commercialOrderId,
        UUID proposalId,
        UUID opportunityId,
        UUID leadId,
        UUID createdBy,
        UUID responsiblePersonId) {}
