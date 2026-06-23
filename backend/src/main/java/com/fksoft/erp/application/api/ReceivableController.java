package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.CreateReceivableRequest;
import com.fksoft.erp.application.api.dto.ReceivableResponse;
import com.fksoft.erp.domain.financial.service.ReceivableService;
import com.fksoft.erp.domain.financial.service.data.CreateReceivableCommand;
import com.fksoft.erp.domain.financial.service.data.EligibleOrder;
import com.fksoft.erp.domain.financial.service.data.InstallmentInput;
import com.fksoft.erp.domain.financial.service.data.ReceivableDetail;
import com.fksoft.erp.domain.financial.service.data.ReceivableListItem;
import com.fksoft.erp.domain.financial.service.data.ReceivableSearchCriteria;
import com.fksoft.erp.infra.security.UserContextProvider;
import com.fksoft.erp.infra.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receivable endpoints (Financial Operations). Creating a Receivable from a Commercial Order with a CONFIRMED
 * booking requires {@code financial:receivable:create}, and the caller must be allowed to see the source Order
 * (the Order read tiers decide that). Creating a Receivable registers no Payment and creates no Commission,
 * Invoice, Booking or Customer Care data. The list and detail are gated by the Financial read tiers
 * ({@code financial:receivable:read} / {@code :read:all}); {@link ReceivableService} narrows visibility.
 */
@RestController
@RequestMapping("/api/receivables")
@RequiredArgsConstructor
public class ReceivableController {

    private final ReceivableService receivableService;
    private final UserContextProvider userContext;

    /**
     * Creates a Receivable from a confirmed Commercial Order (the Receivable starts OPEN).
     *
     * @param request the source order id, the due date and the optional notes / financial responsible
     * @return 201 Created with the new Receivable id and status
     */
    @PostMapping
    public ResponseEntity<ReceivableResponse> create(@Valid @RequestBody CreateReceivableRequest request) {
        List<InstallmentInput> installments = request.installments() == null
                ? List.of()
                : request.installments().stream()
                        .map(i -> new InstallmentInput(i.amount(), i.dueDate(), i.paymentNotes()))
                        .toList();
        CreateReceivableCommand command = new CreateReceivableCommand(
                request.commercialOrderId(),
                request.dueDate(),
                request.paymentNotes(),
                request.financialResponsiblePersonId(),
                installments);
        UUID id = receivableService.create(
                command, userContext.currentUserId(), canSeeAllOrders(), canSeeUnassignedOrders());
        return ResponseEntity.created(URI.create("/api/receivables/" + id)).body(new ReceivableResponse(id, "OPEN"));
    }

    /**
     * Operational, paginated Receivable list visible to the caller, with optional filters. The CANCELLED
     * Receivables are excluded unless the {@code status} filter includes them. The contract carries receivable
     * data only — never Payment, Commission or Invoice data.
     *
     * @param status optional status codes to include (empty ⇒ active only)
     * @param order optional source Commercial Order id to restrict to
     * @param pageable page, size and sort (default: createdAt desc, size 20)
     * @return a page of Receivable list items
     */
    @GetMapping
    public PageResponse<ReceivableListItem> list(
            @RequestParam(required = false) Set<String> status,
            @RequestParam(required = false) UUID order,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        ReceivableSearchCriteria criteria = new ReceivableSearchCriteria(status, order);
        return PageResponse.from(
                receivableService.list(criteria, pageable, userContext.currentUserId(), canSeeAllReceivables()),
                item -> item);
    }

    /**
     * The Commercial Orders eligible to originate a Receivable (booking CONFIRMED, without an active Receivable)
     * visible to the caller — feeds the create form's order selector.
     *
     * @return the eligible orders
     */
    @GetMapping("/eligible-orders")
    public List<EligibleOrder> eligibleOrders() {
        return receivableService.eligibleOrders(
                userContext.currentUserId(), canSeeAllOrders(), canSeeUnassignedOrders());
    }

    /**
     * Full detail of a Receivable the caller may see, keeping the commercial origin traceable. The contract
     * carries receivable data only — never Payment, Commission or Invoice data.
     *
     * @param id the receivable id
     * @return the Receivable detail read model
     */
    @GetMapping("/{id}")
    public ReceivableDetail detail(@PathVariable UUID id) {
        return receivableService.detail(id, userContext.currentUserId(), canSeeAllReceivables());
    }

    // Source-order visibility for creation/eligibility reuses the Order read tiers.
    private boolean canSeeAllOrders() {
        return userContext.hasScope("sales:order:read:all");
    }

    private boolean canSeeUnassignedOrders() {
        return userContext.hasScope("sales:order:read:unassigned");
    }

    // Receivable listing/detail use the Financial read tiers.
    private boolean canSeeAllReceivables() {
        return userContext.hasScope("financial:receivable:read:all");
    }
}
