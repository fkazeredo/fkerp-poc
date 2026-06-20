package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.CreateOrderRequest;
import com.fksoft.erp.application.api.dto.OrderResponse;
import com.fksoft.erp.domain.sales.service.CommercialOrderService;
import com.fksoft.erp.domain.sales.service.data.CommercialOrderDetail;
import com.fksoft.erp.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commercial Order endpoints (Sales &amp; Proposals). Creating an Order from an Accepted Proposal requires
 * {@code sales:order:create}, and the caller must be allowed to see the source Proposal (the Proposal read
 * tiers are reused to decide that). The detail requires an Order read tier — {@code sales:order:read} (own
 * only), {@code sales:order:read:unassigned} (also the unassigned pool) or {@code sales:order:read:all}
 * (all) — the visibility tier being enforced by {@code OrderAccessPolicy}. The contract carries
 * commercial-order data only — never Booking, Receivable, Payment, Commission or Customer Care data.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class CommercialOrderController {

    private final CommercialOrderService orderService;
    private final UserContextProvider userContext;

    /**
     * Creates a Commercial Order from an Accepted Proposal (closing the source Opportunity as won). Creates no
     * Booking, Receivable, Payment, Commission or Customer Care data.
     *
     * @param request the source proposal id
     * @return 201 Created with the new order id
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        UUID id = orderService.create(
                request.proposalId(), userContext.currentUserId(), canSeeAllProposals(), canSeeUnassignedProposals());
        return ResponseEntity.created(URI.create("/api/orders/" + id)).body(new OrderResponse(id));
    }

    /**
     * Full detail of a Commercial Order the caller may see, with the source Proposal, Opportunity and Lead
     * kept traceable.
     *
     * @param id the order id
     * @return the Commercial Order detail read model
     */
    @GetMapping("/{id}")
    public CommercialOrderDetail detail(@PathVariable UUID id) {
        return orderService.detail(id, userContext.currentUserId(), canSeeAllOrders(), canSeeUnassignedOrders());
    }

    // Creating an Order reuses the Proposal read tiers to check the caller may see the source Proposal.
    private boolean canSeeAllProposals() {
        return userContext.hasScope("sales:proposal:read:all");
    }

    private boolean canSeeUnassignedProposals() {
        return userContext.hasScope("sales:proposal:read:unassigned");
    }

    // Order listing/detail uses the Order read tiers.
    private boolean canSeeAllOrders() {
        return userContext.hasScope("sales:order:read:all");
    }

    private boolean canSeeUnassignedOrders() {
        return userContext.hasScope("sales:order:read:unassigned");
    }
}
