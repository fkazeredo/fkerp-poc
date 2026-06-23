package com.fksoft.erp.domain.financial.model;

import java.util.UUID;

/**
 * Domain event published when a Receivable is created from a confirmed Commercial Order — a business fact a
 * later context (Commission Management in Sprint 6) may react to. It carries no transport concern and implies
 * no Payment or Commission.
 *
 * @param receivableId the new Receivable id
 * @param commercialOrderId the source Commercial Order id
 * @param customerId the payer (Customer) id
 * @param createdBy id of the user who created the Receivable
 */
public record ReceivableCreated(UUID receivableId, UUID commercialOrderId, UUID customerId, UUID createdBy) {}
