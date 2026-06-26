package com.fksoft.erp.domain.commission.model;

import java.util.UUID;

/**
 * Domain event published when a Commission's status is established or changes — on generation ({@code EXPECTED}, or
 * {@code ELIGIBLE} when the receivable was already paid), when it becomes eligible (the receivable was paid), and on
 * approval / rejection / cancellation / payment. It is the business fact the Sales &amp; Proposals context reacts to in
 * order to reflect the commission status summary onto its Commercial Order (without Commission Management taking
 * ownership of the Order). It carries no transport concern and changes no Receivable or Payment data.
 *
 * @param commissionId the Commission whose status changed
 * @param commercialOrderId the source Commercial Order the status is reflected onto
 * @param status the Commission status code (e.g. {@code EXPECTED}, {@code ELIGIBLE}, {@code APPROVED}, {@code PAID},
 *     {@code REJECTED}, {@code CANCELLED})
 */
public record CommissionStatusChanged(UUID commissionId, UUID commercialOrderId, String status) {}
