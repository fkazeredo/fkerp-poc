package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.model.OpportunityStage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Query parameters for the operational Opportunity list (all optional). Spring binds them from the
 * request; the controller maps them to the domain
 * {@link com.fksoft.erp.domain.crm.service.data.OpportunitySearchCriteria}. Grouping the filters in a
 * single object keeps the listing endpoint within the parameter limit and isolates the transport shape
 * (raw {@code responsible} token, ISO calendar dates) from the use-case input (resolved ids, instants).
 *
 * @param stage stages to include (empty ⇒ all non-lost; include LOST to see lost Opportunities)
 * @param responsible a responsible user id, or the literal {@code unassigned} for the unassigned pool
 * @param originId restrict to this Lead origin
 * @param createdFrom inclusive lower bound on the creation date (ISO date)
 * @param createdTo inclusive upper bound on the creation date (ISO date)
 * @param closeFrom inclusive lower bound on the expected closing date (ISO date)
 * @param closeTo inclusive upper bound on the expected closing date (ISO date)
 * @param valueMin inclusive lower bound on the estimated value
 * @param valueMax inclusive upper bound on the estimated value
 * @param q free-text search over the Opportunity (title/product/interest) and the source Lead (name and
 *     contacts)
 */
public record OpportunityListParams(
        Set<OpportunityStage> stage,
        String responsible,
        UUID originId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closeFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closeTo,
        BigDecimal valueMin,
        BigDecimal valueMax,
        String q) {}
