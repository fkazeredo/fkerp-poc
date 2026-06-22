package com.fksoft.erp.domain.booking.model;

import java.util.UUID;

/**
 * Domain event published when a Booking Request's status is (re)consolidated from its booking items and
 * attempts — a business fact the Sales &amp; Proposals context reacts to in order to reflect the consolidated
 * booking status onto its Commercial Order (without Booking taking ownership of the Order). It carries no
 * transport concern.
 *
 * @param bookingRequestId the Booking Request whose status was consolidated
 * @param commercialOrderId the source Commercial Order the status is reflected onto
 * @param status the consolidated Booking Request status
 */
public record BookingStatusConsolidated(UUID bookingRequestId, UUID commercialOrderId, BookingRequestStatus status) {}
