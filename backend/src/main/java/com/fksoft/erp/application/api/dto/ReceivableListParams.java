package com.fksoft.erp.application.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Query parameters for the operational Receivable list (all optional). Spring binds them from the request; the
 * controller maps them to the domain
 * {@link com.fksoft.erp.domain.financial.service.data.ReceivableSearchCriteria}.
 *
 * @param status statuses to include (empty ⇒ operational: OPEN / PARTIALLY_PAID / OVERDUE)
 * @param order restrict to a single source Commercial Order id
 * @param orderNumber restrict to a single source Commercial Order by its number (PC-000n)
 * @param payer a case-insensitive substring of the payer (customer) name
 * @param dueFrom inclusive lower bound on the due date (ISO date)
 * @param dueTo inclusive upper bound on the due date (ISO date)
 * @param createdFrom inclusive lower bound on the creation date (ISO date)
 * @param createdTo inclusive upper bound on the creation date (ISO date)
 * @param commercialResponsible a commercial-responsible user id
 * @param financialResponsible a financial-responsible user id
 * @param amountMin inclusive lower bound on the total amount
 * @param amountMax inclusive upper bound on the total amount
 * @param overdueOnly when {@code true}, only the past-due receivables still requiring follow-up
 */
public record ReceivableListParams(
        Set<String> status,
        UUID order,
        Long orderNumber,
        String payer,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
        UUID commercialResponsible,
        UUID financialResponsible,
        BigDecimal amountMin,
        BigDecimal amountMax,
        Boolean overdueOnly) {}
