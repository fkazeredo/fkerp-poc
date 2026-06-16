package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.LoseRequest;
import com.fksoft.erp.application.api.dto.OpportunityCreateRequest;
import com.fksoft.erp.application.api.dto.OpportunityListParams;
import com.fksoft.erp.application.api.dto.OpportunityResponse;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.service.OpportunityService;
import com.fksoft.erp.domain.crm.service.data.CreateOpportunityCommand;
import com.fksoft.erp.domain.crm.service.data.OpportunityDetail;
import com.fksoft.erp.domain.crm.service.data.OpportunityListItem;
import com.fksoft.erp.domain.crm.service.data.OpportunitySearchCriteria;
import com.fksoft.erp.infra.security.UserContextProvider;
import com.fksoft.erp.infra.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commercial Opportunity endpoints. Creating an Opportunity from a Qualified Lead requires
 * {@code crm:opportunity:create}, and the caller must be allowed to see the source Lead (the lead read
 * tiers are reused to decide that). Listing and detail require an Opportunity read tier —
 * {@code crm:opportunity:read} (own only), {@code crm:opportunity:read:unassigned} (also the unassigned
 * pool) or {@code crm:opportunity:read:all} (all) — the visibility tier being enforced by the policy at
 * the query/record level, so filters, search and detail can never expose Opportunities the caller may
 * not see. Marking an Opportunity as lost requires {@code crm:opportunity:update} and that the caller may
 * see it; the transition returns the refreshed detail.
 */
@RestController
@RequestMapping("/api/opportunities")
@RequiredArgsConstructor
public class OpportunityController {

    private final OpportunityService opportunityService;
    private final UserContextProvider userContext;

    /**
     * Creates an Opportunity from a Qualified Lead (status NEW_OPPORTUNITY).
     *
     * @param request the opportunity data
     * @return 201 Created with the new opportunity id and stage
     */
    @PostMapping
    public ResponseEntity<OpportunityResponse> create(@Valid @RequestBody OpportunityCreateRequest request) {
        CreateOpportunityCommand command = new CreateOpportunityCommand(
                request.leadId(),
                request.responsiblePersonId(),
                request.productType(),
                request.estimatedValue(),
                request.expectedCloseDate(),
                request.initialNote());
        UUID id = opportunityService.create(
                command, userContext.currentUserId(), canSeeAllLeads(), canSeeUnassignedLeads());
        return ResponseEntity.created(URI.create("/api/opportunities/" + id))
                .body(new OpportunityResponse(id, OpportunityStage.NEW_OPPORTUNITY));
    }

    /**
     * Operational, paginated list of Opportunities visible to the caller, with optional filters and
     * search. Lost Opportunities are excluded unless the {@code stage} filter explicitly includes LOST.
     *
     * @param params the optional filters and search term (see {@link OpportunityListParams})
     * @param pageable page, size and sort (default: createdAt desc, size 20)
     * @return a page of operational Opportunity items
     */
    @GetMapping
    public PageResponse<OpportunityListItem> list(
            OpportunityListParams params,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        boolean unassignedOnly = "unassigned".equalsIgnoreCase(params.responsible());
        UUID responsibleId = (!unassignedOnly
                        && params.responsible() != null
                        && !params.responsible().isBlank())
                ? UUID.fromString(params.responsible())
                : null;
        OpportunitySearchCriteria criteria = new OpportunitySearchCriteria(
                params.stage(),
                responsibleId,
                unassignedOnly,
                params.originId(),
                toStartOfDayUtc(params.createdFrom()),
                toStartOfDayUtc(params.createdTo() != null ? params.createdTo().plusDays(1) : null),
                params.closeFrom(),
                params.closeTo(),
                params.valueMin(),
                params.valueMax(),
                params.q());
        return PageResponse.from(
                opportunityService.list(
                        criteria,
                        pageable,
                        userContext.currentUserId(),
                        canSeeAllOpportunities(),
                        canSeeUnassignedOpportunities()),
                item -> item);
    }

    /**
     * Full detail of an Opportunity the caller may see, with the source Lead kept traceable.
     *
     * @param id the opportunity id
     * @return the Opportunity detail read model
     */
    @GetMapping("/{id}")
    public OpportunityDetail detail(@PathVariable UUID id) {
        return opportunityService.detail(
                id, userContext.currentUserId(), canSeeAllOpportunities(), canSeeUnassignedOpportunities());
    }

    /**
     * Marks an Opportunity as lost with a reason; returns the refreshed detail. The source Lead is not
     * affected.
     *
     * @param id the opportunity id
     * @param request the loss reason and optional note
     * @return the updated detail
     */
    @PostMapping("/{id}/lose")
    public OpportunityDetail lose(@PathVariable UUID id, @Valid @RequestBody LoseRequest request) {
        return opportunityService.markLost(
                id,
                request.lossReasonId(),
                request.note(),
                userContext.currentUserId(),
                canSeeAllOpportunities(),
                canSeeUnassignedOpportunities());
    }

    // The creation period is given as calendar dates; the column is an instant, so anchor at UTC midnight
    // (the upper bound is pre-incremented by a day by the caller, making the range [from, to) exclusive).
    private static Instant toStartOfDayUtc(LocalDate date) {
        return date != null ? date.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }

    // Source-lead visibility for creation reuses the Lead read tiers.
    private boolean canSeeAllLeads() {
        return userContext.hasScope("crm:lead:read:all");
    }

    private boolean canSeeUnassignedLeads() {
        return userContext.hasScope("crm:lead:read:unassigned");
    }

    // Opportunity listing uses the Opportunity read tiers.
    private boolean canSeeAllOpportunities() {
        return userContext.hasScope("crm:opportunity:read:all");
    }

    private boolean canSeeUnassignedOpportunities() {
        return userContext.hasScope("crm:opportunity:read:unassigned");
    }
}
