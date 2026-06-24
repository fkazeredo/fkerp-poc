package com.fksoft.erp.domain.financial.model;

import java.util.UUID;

/**
 * Domain event published when a Receivable's status is established or changes — on creation ({@code OPEN}), after
 * each payment (the consolidated status) and when the daily overdue check flags it {@code OVERDUE}. It is the
 * business fact the Sales &amp; Proposals context reacts to in order to reflect the financial status onto its
 * Commercial Order (without Financial Operations taking ownership of the Order). It carries no transport concern
 * and implies no Commission.
 *
 * @param receivableId the Receivable whose status changed
 * @param commercialOrderId the source Commercial Order the status is reflected onto
 * @param status the Receivable status code (e.g. {@code OPEN}, {@code PARTIALLY_PAID}, {@code PAID}, {@code OVERDUE})
 */
public record ReceivableStatusChanged(UUID receivableId, UUID commercialOrderId, String status) {}
