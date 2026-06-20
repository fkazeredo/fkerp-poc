package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.CommercialOrderListParams;
import com.fksoft.erp.application.api.dto.CreateOrderRequest;
import com.fksoft.erp.application.api.dto.OrderResponse;
import com.fksoft.erp.domain.sales.service.CommercialOrderService;
import com.fksoft.erp.domain.sales.service.data.CommercialOrderDetail;
import com.fksoft.erp.domain.sales.service.data.CommercialOrderListItem;
import com.fksoft.erp.domain.sales.service.data.CommercialOrderSearchCriteria;
import com.fksoft.erp.domain.sales.service.data.OrderIndicators;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * Operational, paginated list of Commercial Orders visible to the caller, with optional filters. Cancelled
     * Orders are excluded unless the {@code status} filter explicitly includes them. The contract carries
     * commercial-order data only — never Booking, Receivable, Payment, Commission or Customer Care data.
     *
     * @param params the optional filters (see {@link CommercialOrderListParams})
     * @param pageable page, size and sort (default: createdAt desc, size 20)
     * @return a page of Commercial Order list items
     */
    @GetMapping
    public PageResponse<CommercialOrderListItem> list(
            CommercialOrderListParams params,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        boolean unassignedOnly = "unassigned".equalsIgnoreCase(params.responsible());
        UUID responsibleId = (!unassignedOnly
                        && params.responsible() != null
                        && !params.responsible().isBlank())
                ? UUID.fromString(params.responsible())
                : null;
        CommercialOrderSearchCriteria criteria = new CommercialOrderSearchCriteria(
                params.status(),
                responsibleId,
                unassignedOnly,
                toStartOfDayUtc(params.createdFrom()),
                toStartOfDayUtc(params.createdTo() != null ? params.createdTo().plusDays(1) : null),
                params.totalMin(),
                params.totalMax(),
                params.bookingNeed(),
                params.q());
        return PageResponse.from(
                orderService.list(
                        criteria, pageable, userContext.currentUserId(), canSeeAllOrders(), canSeeUnassignedOrders()),
                item -> item);
    }

    /**
     * Minimum Commercial Order indicators for the caller: volume figures over the requested period (by
     * creation date) plus a current operational snapshot (pending booking). Read-only; never exposes
     * Booking, Receivable, Payment, Commission or Customer Care data.
     *
     * @param createdFrom optional inclusive lower bound on the creation date (ISO date)
     * @param createdTo optional inclusive upper bound on the creation date (ISO date)
     * @return the indicators
     */
    @GetMapping("/indicators")
    public OrderIndicators indicators(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo) {
        return orderService.indicators(
                userContext.currentUserId(),
                canSeeAllOrders(),
                canSeeUnassignedOrders(),
                toStartOfDayUtc(createdFrom),
                toStartOfDayUtc(createdTo != null ? createdTo.plusDays(1) : null));
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

    // The creation period is given as calendar dates; the column is an instant, so anchor at UTC midnight
    // (the upper bound is pre-incremented by a day, making the range [from, to) exclusive).
    private static Instant toStartOfDayUtc(LocalDate date) {
        return date != null ? date.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
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
