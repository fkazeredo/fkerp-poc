package com.fksoft.erp.domain.financial.service.data;

import java.util.Set;
import java.util.UUID;

/**
 * Optional filters for the operational Receivable list. An empty status set means the active (non-cancelled)
 * Receivables.
 *
 * @param statuses statuses to include (empty/null ⇒ active, i.e. excluding CANCELLED)
 * @param commercialOrderId restrict to a single source Commercial Order, or {@code null}
 */
public record ReceivableSearchCriteria(Set<String> statuses, UUID commercialOrderId) {}
