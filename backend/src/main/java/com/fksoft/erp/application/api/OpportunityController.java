package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.OpportunityCreateRequest;
import com.fksoft.erp.application.api.dto.OpportunityResponse;
import com.fksoft.erp.domain.crm.CreateOpportunityCommand;
import com.fksoft.erp.domain.crm.OpportunityService;
import com.fksoft.erp.domain.crm.OpportunityStage;
import com.fksoft.erp.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commercial Opportunity endpoints. Creating an Opportunity from a Qualified Lead requires
 * {@code crm:opportunity:create}, and the caller must be allowed to see the source Lead (the lead read
 * tiers are reused to decide that).
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
        UUID id = opportunityService.create(command, userContext.currentUserId(), canSeeAll(), canSeeUnassigned());
        return ResponseEntity.created(URI.create("/api/opportunities/" + id))
                .body(new OpportunityResponse(id, OpportunityStage.NEW_OPPORTUNITY));
    }

    private boolean canSeeAll() {
        return userContext.hasScope("crm:lead:read:all");
    }

    private boolean canSeeUnassigned() {
        return userContext.hasScope("crm:lead:read:unassigned");
    }
}
