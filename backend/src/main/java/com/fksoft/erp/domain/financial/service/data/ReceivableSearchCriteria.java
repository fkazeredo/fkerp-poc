package com.fksoft.erp.domain.financial.service.data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Optional filters for the operational Receivable list. An empty status set means the operational receivables
 * (excluding the settled {@code PAID} and {@code CANCELLED}); pass them explicitly in the status filter to see
 * them.
 *
 * @param statuses statuses to include (empty/null ⇒ operational: OPEN / PARTIALLY_PAID / OVERDUE)
 * @param commercialOrderId restrict to a single source Commercial Order id, or {@code null}
 * @param orderNumber restrict to a single source Commercial Order by its number (PC-000n), or {@code null}
 * @param payer a case-insensitive substring of the payer (customer) name, or {@code null}
 * @param dueFrom inclusive lower bound on the due date, or {@code null}
 * @param dueTo inclusive upper bound on the due date, or {@code null}
 * @param createdFrom inclusive lower bound on the creation instant, or {@code null}
 * @param createdTo exclusive upper bound on the creation instant, or {@code null}
 * @param commercialResponsibleId restrict to a commercial responsible, or {@code null}
 * @param financialResponsibleId restrict to a financial responsible, or {@code null}
 * @param amountMin inclusive lower bound on the total amount, or {@code null}
 * @param amountMax inclusive upper bound on the total amount, or {@code null}
 * @param overdueOnly when {@code true}, only the past-due receivables still requiring follow-up
 */
public record ReceivableSearchCriteria(
        Set<String> statuses,
        UUID commercialOrderId,
        Long orderNumber,
        String payer,
        LocalDate dueFrom,
        LocalDate dueTo,
        Instant createdFrom,
        Instant createdTo,
        UUID commercialResponsibleId,
        UUID financialResponsibleId,
        BigDecimal amountMin,
        BigDecimal amountMax,
        boolean overdueOnly) {}
