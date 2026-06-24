package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.CreateReceivableRequest;
import com.fksoft.erp.application.api.dto.ReceivableListParams;
import com.fksoft.erp.application.api.dto.ReceivableResponse;
import com.fksoft.erp.application.api.dto.RegisterPaymentRequest;
import com.fksoft.erp.application.api.dto.ReversePaymentRequest;
import com.fksoft.erp.domain.financial.service.ReceivableService;
import com.fksoft.erp.domain.financial.service.data.CreateReceivableCommand;
import com.fksoft.erp.domain.financial.service.data.EligibleOrder;
import com.fksoft.erp.domain.financial.service.data.InstallmentInput;
import com.fksoft.erp.domain.financial.service.data.ReceivableDetail;
import com.fksoft.erp.domain.financial.service.data.ReceivableListItem;
import com.fksoft.erp.domain.financial.service.data.ReceivableSearchCriteria;
import com.fksoft.erp.domain.financial.service.data.RegisterPaymentCommand;
import com.fksoft.erp.infra.security.UserContextProvider;
import com.fksoft.erp.infra.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
     * Operational, paginated Receivable list visible to the caller, with optional filters — the receivables that
     * require financial follow-up. The settled {@code PAID} and {@code CANCELLED} receivables are excluded unless
     * the {@code status} filter includes them; overdue receivables stay visible as operational problems. The
     * contract carries receivable + commercial-origin data only — never Payment, Commission, Invoice or
     * bank-reconciliation data.
     *
     * @param params the optional filters (see {@link ReceivableListParams})
     * @param pageable page, size and sort (default: createdAt desc, size 20)
     * @return a page of Receivable list items
     */
    @GetMapping
    public PageResponse<ReceivableListItem> list(
            ReceivableListParams params,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        ReceivableSearchCriteria criteria = new ReceivableSearchCriteria(
                params.status(),
                params.order(),
                params.orderNumber(),
                params.payer(),
                params.dueFrom(),
                params.dueTo(),
                toStartOfDayUtc(params.createdFrom()),
                toStartOfDayUtc(params.createdTo() != null ? params.createdTo().plusDays(1) : null),
                params.commercialResponsible(),
                params.financialResponsible(),
                params.amountMin(),
                params.amountMax(),
                params.overdueOnly() != null && params.overdueOnly());
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
     * carries receivable + installment + payment-history data only — never Commission, Invoice or
     * bank-reconciliation data.
     *
     * @param id the receivable id
     * @return the Receivable detail read model
     */
    @GetMapping("/{id}")
    public ReceivableDetail detail(@PathVariable UUID id) {
        return receivableService.detail(id, userContext.currentUserId(), canSeeAllReceivables());
    }

    /**
     * Registers a full payment for one installment of a Receivable. The installment becomes Paid; when every
     * installment is paid the Receivable becomes Paid (otherwise Partially paid). Requires
     * {@code financial:payment:register} and that the caller may see the Receivable. Registers no Commission,
     * Invoice or bank-reconciliation data.
     *
     * @param id the receivable id
     * @param installmentId the target installment id
     * @param request the payment data (method, amount, date, optional note)
     * @return the refreshed Receivable detail
     */
    @PostMapping("/{id}/installments/{installmentId}/payments")
    public ReceivableDetail registerPayment(
            @PathVariable UUID id,
            @PathVariable UUID installmentId,
            @Valid @RequestBody RegisterPaymentRequest request) {
        RegisterPaymentCommand command = new RegisterPaymentCommand(
                request.paymentMethodId(), request.amount(), request.paymentDate(), request.note());
        return receivableService.registerPayment(
                id, installmentId, command, userContext.currentUserId(), canSeeAllReceivables());
    }

    /**
     * Reverses a registered payment of a Receivable (a payment-entry correction). The payment stays in history
     * marked reversed, and the installment / Receivable status and paid amount are re-consolidated. Requires
     * {@code financial:payment:reverse} and that the caller may see the Receivable. Creates no refund, Commission
     * or Customer Care data.
     *
     * @param id the receivable id
     * @param paymentId the payment to reverse
     * @param request the reversal data (the required reason)
     * @return the refreshed Receivable detail
     */
    @PostMapping("/{id}/payments/{paymentId}/reversals")
    public ReceivableDetail reversePayment(
            @PathVariable UUID id, @PathVariable UUID paymentId, @Valid @RequestBody ReversePaymentRequest request) {
        return receivableService.reversePayment(
                id, paymentId, request.reason(), userContext.currentUserId(), canSeeAllReceivables());
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

    // The creation period is given as calendar dates; createdAt is an instant, so anchor at UTC midnight (the
    // upper bound is pre-incremented by a day, making the range [from, to) exclusive).
    private static Instant toStartOfDayUtc(LocalDate date) {
        return date != null ? date.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }
}
