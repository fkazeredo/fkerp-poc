package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.sales.model.ProposalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for the operational Proposal list (all optional). Empty {@code statuses} means the
 * default operational view, which excludes the terminal-negative outcomes (REJECTED, EXPIRED, CANCELLED);
 * include them explicitly to see inactive Proposals.
 *
 * @param statuses statuses to include (empty/null ⇒ all open, i.e. excluding REJECTED/EXPIRED/CANCELLED)
 * @param responsibleId restrict to this responsible user
 * @param unassignedOnly restrict to Proposals with no responsible (takes precedence over responsibleId)
 * @param opportunityId restrict to Proposals of this source Opportunity (deep-link from an Opportunity)
 * @param createdFrom inclusive lower bound on the creation instant
 * @param createdTo exclusive upper bound on the creation instant
 * @param validFrom inclusive lower bound on the validity date
 * @param validTo inclusive upper bound on the validity date
 * @param totalMin inclusive lower bound on the total amount
 * @param totalMax inclusive upper bound on the total amount
 * @param query free-text search over the Proposal title and the source Opportunity name
 */
public record ProposalSearchCriteria(
        Set<ProposalStatus> statuses,
        UUID responsibleId,
        boolean unassignedOnly,
        UUID opportunityId,
        Instant createdFrom,
        Instant createdTo,
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal totalMin,
        BigDecimal totalMax,
        String query) {}
