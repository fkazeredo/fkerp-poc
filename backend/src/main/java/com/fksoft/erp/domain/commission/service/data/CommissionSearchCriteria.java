package com.fksoft.erp.domain.commission.service.data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Optional filters for the operational Commission list. An empty status set means the operational commissions
 * (EXPECTED / ELIGIBLE / APPROVED); pass {@code PAID} / {@code REJECTED} / {@code CANCELLED} explicitly to see them.
 *
 * @param statuses statuses to include (empty/null ⇒ operational: EXPECTED / ELIGIBLE / APPROVED)
 * @param beneficiaryUserId restrict to a single beneficiary user id, or {@code null}
 * @param commercialOrderId restrict to a single source Commercial Order id, or {@code null}
 * @param orderNumber restrict to a single source Commercial Order by its number (PC-000n), or {@code null}
 * @param ruleId restrict to a single applied Commission Rule, or {@code null}
 * @param createdFrom inclusive lower bound on the creation instant, or {@code null}
 * @param createdTo exclusive upper bound on the creation instant, or {@code null}
 * @param eligibleFrom inclusive lower bound on the eligibility instant, or {@code null}
 * @param eligibleTo exclusive upper bound on the eligibility instant, or {@code null}
 * @param paidFrom inclusive lower bound on the payment instant, or {@code null}
 * @param paidTo exclusive upper bound on the payment instant, or {@code null}
 * @param amountMin inclusive lower bound on the commission amount, or {@code null}
 * @param amountMax inclusive upper bound on the commission amount, or {@code null}
 */
public record CommissionSearchCriteria(
        Set<String> statuses,
        UUID beneficiaryUserId,
        UUID commercialOrderId,
        Long orderNumber,
        UUID ruleId,
        Instant createdFrom,
        Instant createdTo,
        Instant eligibleFrom,
        Instant eligibleTo,
        Instant paidFrom,
        Instant paidTo,
        BigDecimal amountMin,
        BigDecimal amountMax) {}
