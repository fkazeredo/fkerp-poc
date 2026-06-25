package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.CommissionListParams;
import com.fksoft.erp.application.api.dto.CommissionResponse;
import com.fksoft.erp.application.api.dto.GenerateCommissionRequest;
import com.fksoft.erp.domain.commission.service.CommissionService;
import com.fksoft.erp.domain.commission.service.data.CommissionDetail;
import com.fksoft.erp.domain.commission.service.data.CommissionListItem;
import com.fksoft.erp.domain.commission.service.data.CommissionSearchCriteria;
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
 * Commission endpoints (Commission Management). Generating an Expected Commission from a closed Commercial Order
 * requires {@code commission:create}; the operational list and the detail are gated by the Commission read tiers
 * ({@code commission:read} own / {@code commission:read:all} all) and narrowed by {@link CommissionService} so a
 * seller/representative sees only their own commissions. Reading a commission creates no Commission Payment, Accounts
 * Payable, payroll, tax or accounting data.
 */
@RestController
@RequestMapping("/api/commissions")
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionService commissionService;
    private final UserContextProvider userContext;

    /**
     * Generates an Expected Commission from a commercially-closed Commercial Order.
     *
     * @param request the source order id
     * @return 201 Created with the new commission id
     */
    @PostMapping
    public ResponseEntity<CommissionResponse> generate(@Valid @RequestBody GenerateCommissionRequest request) {
        UUID id = commissionService.generate(
                request.commercialOrderId(),
                userContext.currentUserId(),
                userContext.hasScope("sales:order:read:all"),
                userContext.hasScope("sales:order:read:unassigned"));
        return ResponseEntity.created(URI.create("/api/commissions/" + id)).body(new CommissionResponse(id));
    }

    /**
     * Operational, paginated Commission list visible to the caller, with optional filters — for tracking expected,
     * eligible, approved and paid commissions. The settled {@code PAID} and the terminal {@code REJECTED}/
     * {@code CANCELLED} are excluded unless the {@code status} filter includes them. Carries commission +
     * commercial-origin data only — never payroll, tax, accounting or accounts-payable data.
     *
     * @param params the optional filters (see {@link CommissionListParams})
     * @param pageable page, size and sort (default: createdAt desc, size 20)
     * @return a page of Commission list items
     */
    @GetMapping
    public PageResponse<CommissionListItem> list(
            CommissionListParams params,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        CommissionSearchCriteria criteria = new CommissionSearchCriteria(
                params.status(),
                params.beneficiary(),
                params.order(),
                params.orderNumber(),
                params.rule(),
                toStartOfDayUtc(params.createdFrom()),
                toStartOfDayUtc(params.createdTo() != null ? params.createdTo().plusDays(1) : null),
                toStartOfDayUtc(params.eligibleFrom()),
                toStartOfDayUtc(
                        params.eligibleTo() != null ? params.eligibleTo().plusDays(1) : null),
                toStartOfDayUtc(params.paidFrom()),
                toStartOfDayUtc(params.paidTo() != null ? params.paidTo().plusDays(1) : null),
                params.amountMin(),
                params.amountMax());
        return PageResponse.from(
                commissionService.list(criteria, pageable, userContext.currentUserId(), canSeeAllCommissions()),
                item -> item);
    }

    /**
     * Full detail of a Commission the caller may see.
     *
     * @param id the commission id
     * @return the commission detail
     */
    @GetMapping("/{id}")
    public CommissionDetail detail(@PathVariable UUID id) {
        return commissionService.detail(id, userContext.currentUserId(), canSeeAllCommissions());
    }

    private boolean canSeeAllCommissions() {
        return userContext.hasScope("commission:read:all");
    }

    // The filter periods are given as calendar dates; the stored fields are instants, so anchor at UTC midnight (the
    // upper bounds are pre-incremented by a day, making each range [from, to) exclusive).
    private static Instant toStartOfDayUtc(LocalDate date) {
        return date != null ? date.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }
}
