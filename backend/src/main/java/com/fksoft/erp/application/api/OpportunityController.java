package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.OpportunityCreateRequest;
import com.fksoft.erp.application.api.dto.OpportunityListItemResponse;
import com.fksoft.erp.application.api.dto.OpportunityResponse;
import com.fksoft.erp.application.read.OpportunityReadService;
import com.fksoft.erp.domain.crm.CreateOpportunityCommand;
import com.fksoft.erp.domain.crm.OpportunitySearchCriteria;
import com.fksoft.erp.domain.crm.OpportunityService;
import com.fksoft.erp.domain.crm.OpportunityStage;
import com.fksoft.erp.infra.security.UserContextProvider;
import com.fksoft.erp.infra.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commercial Opportunity endpoints. Creating an Opportunity from a Qualified Lead requires
 * {@code crm:opportunity:create}, and the caller must be allowed to see the source Lead (the lead read
 * tiers are reused to decide that). Listing is served by {@link OpportunityReadService} and requires an
 * Opportunity read tier — {@code crm:opportunity:read} (own only),
 * {@code crm:opportunity:read:unassigned} (also the unassigned pool) or
 * {@code crm:opportunity:read:all} (all) — the visibility tier being enforced by the policy.
 */
@RestController
@RequestMapping("/api/opportunities")
@RequiredArgsConstructor
public class OpportunityController {

    private final OpportunityService opportunityService;
    private final OpportunityReadService opportunityReadService;
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
     * Operational, paginated list of Opportunities visible to the caller, with an optional stage
     * filter and search. Lost Opportunities are excluded unless the {@code stage} filter explicitly
     * includes LOST.
     *
     * @param stage optional stage filter (repeatable); empty means all non-lost
     * @param q optional case-insensitive search over title, product type and main interest
     * @param pageable page, size and sort (default: createdAt desc, size 20)
     * @return a page of operational Opportunity items
     */
    @GetMapping
    public PageResponse<OpportunityListItemResponse> list(
            @RequestParam(required = false) Set<OpportunityStage> stage,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        OpportunitySearchCriteria criteria = new OpportunitySearchCriteria(stage, q);
        return PageResponse.from(
                opportunityReadService.list(
                        criteria,
                        pageable,
                        userContext.currentUserId(),
                        canSeeAllOpportunities(),
                        canSeeUnassignedOpportunities()),
                item -> item);
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
