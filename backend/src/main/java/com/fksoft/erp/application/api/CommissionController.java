package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.CommissionResponse;
import com.fksoft.erp.application.api.dto.GenerateCommissionRequest;
import com.fksoft.erp.domain.commission.service.CommissionService;
import com.fksoft.erp.domain.commission.service.data.CommissionDetail;
import com.fksoft.erp.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commission endpoints (Commission Management). Generating an Expected Commission from a closed Commercial Order
 * requires {@code commission:create}; reading a commission requires {@code commission:read}. The caller must also be
 * allowed to see the source Order (the Order read tiers are read here and passed to the domain service). Generating a
 * commission creates no Commission Payment, Accounts Payable, payroll, tax or accounting data, and never modifies the
 * Order or the Receivable.
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
     * The active Commission of a Commercial Order (0 or 1) — feeds the Order detail's "this order's commission" view,
     * showing whether it is still a forecast (Expected) or eligible for approval (Eligible).
     *
     * @param commercialOrderId the source order id
     * @return the order's active commission detail as a 0-or-1 list
     */
    @GetMapping
    public List<CommissionDetail> byOrder(@RequestParam UUID commercialOrderId) {
        return commissionService.byOrder(commercialOrderId);
    }

    /**
     * Full detail of a Commission.
     *
     * @param id the commission id
     * @return the commission detail
     */
    @GetMapping("/{id}")
    public CommissionDetail detail(@PathVariable UUID id) {
        return commissionService.detail(id);
    }
}
