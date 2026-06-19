package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.sales.model.ProposalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Query parameters for the operational Proposal list (all optional). Spring binds them from the request;
 * the controller maps them to the domain
 * {@link com.fksoft.erp.domain.sales.service.data.ProposalSearchCriteria}. Grouping the filters in a single
 * object keeps the listing endpoint within the parameter limit and isolates the transport shape (raw
 * {@code responsible} token, ISO calendar dates) from the use-case input (resolved ids, instants).
 *
 * @param status statuses to include (empty ⇒ all open; include REJECTED/EXPIRED/CANCELLED to see inactive)
 * @param responsible a responsible user id, or the literal {@code unassigned} for the unassigned pool
 * @param opportunityId restrict to Proposals of this source Opportunity (deep-link)
 * @param createdFrom inclusive lower bound on the creation date (ISO date)
 * @param createdTo inclusive upper bound on the creation date (ISO date)
 * @param validFrom inclusive lower bound on the validity date (ISO date)
 * @param validTo inclusive upper bound on the validity date (ISO date)
 * @param totalMin inclusive lower bound on the total amount
 * @param totalMax inclusive upper bound on the total amount
 * @param q free-text search over the Proposal title and the source Opportunity name
 */
public record ProposalListParams(
        Set<ProposalStatus> status,
        String responsible,
        UUID opportunityId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo,
        BigDecimal totalMin,
        BigDecimal totalMax,
        String q) {}
