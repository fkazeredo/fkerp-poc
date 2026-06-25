package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.CommissionResponse;
import com.fksoft.erp.application.api.dto.GenerateCommissionRequest;
import com.fksoft.erp.domain.commission.service.CommissionService;
import com.fksoft.erp.domain.commission.service.data.CommissionDetail;
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
     * Full detail of an Expected Commission.
     *
     * @param id the commission id
     * @return the commission detail
     */
    @GetMapping("/{id}")
    public CommissionDetail detail(@PathVariable UUID id) {
        return commissionService.detail(id);
    }
}
