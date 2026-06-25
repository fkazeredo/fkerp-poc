package com.fksoft.erp.application.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Query parameters for the operational Commission list (all optional). Spring binds them from the request; the
 * controller maps them to the domain
 * {@link com.fksoft.erp.domain.commission.service.data.CommissionSearchCriteria}.
 *
 * @param status statuses to include (empty ⇒ operational: EXPECTED / ELIGIBLE / APPROVED)
 * @param beneficiary restrict to a single beneficiary user id
 * @param order restrict to a single source Commercial Order id
 * @param orderNumber restrict to a single source Commercial Order by its number (PC-000n)
 * @param rule restrict to a single applied Commission Rule id
 * @param createdFrom inclusive lower bound on the creation date (ISO date)
 * @param createdTo inclusive upper bound on the creation date (ISO date)
 * @param eligibleFrom inclusive lower bound on the eligibility date (ISO date)
 * @param eligibleTo inclusive upper bound on the eligibility date (ISO date)
 * @param paidFrom inclusive lower bound on the payment date (ISO date)
 * @param paidTo inclusive upper bound on the payment date (ISO date)
 * @param amountMin inclusive lower bound on the commission amount
 * @param amountMax inclusive upper bound on the commission amount
 */
public record CommissionListParams(
        Set<String> status,
        UUID beneficiary,
        UUID order,
        Long orderNumber,
        UUID rule,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eligibleFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eligibleTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidTo,
        BigDecimal amountMin,
        BigDecimal amountMax) {}
