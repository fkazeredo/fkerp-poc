package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.MarkProposalSentRequest;
import com.fksoft.erp.application.api.dto.ProposalCreateRequest;
import com.fksoft.erp.application.api.dto.ProposalItemRequest;
import com.fksoft.erp.application.api.dto.ProposalListParams;
import com.fksoft.erp.application.api.dto.ProposalResponse;
import com.fksoft.erp.application.api.dto.ProposalUpdateRequest;
import com.fksoft.erp.application.api.dto.RejectProposalRequest;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import com.fksoft.erp.domain.sales.service.ProposalService;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalDetail;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalListItem;
import com.fksoft.erp.domain.sales.service.data.ProposalSearchCriteria;
import com.fksoft.erp.domain.sales.service.data.UpdateProposalCommand;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commercial Proposal endpoints (Sales &amp; Proposals). Creating a Proposal from a READY_FOR_PROPOSAL
 * Opportunity requires {@code sales:proposal:create}, and the caller must be allowed to see the source
 * Opportunity (the Opportunity read tiers are reused to decide that). Listing and detail require a
 * Proposal read tier — {@code sales:proposal:read} (own only), {@code sales:proposal:read:unassigned}
 * (also the unassigned pool) or {@code sales:proposal:read:all} (all) — the visibility tier being enforced
 * by {@code ProposalAccessPolicy} at the query/record level. The contract carries commercial-offer data
 * only — never Sale, Sales Order, Booking, Financial, Payment or Commission data.
 */
@RestController
@RequestMapping("/api/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final ProposalService proposalService;
    private final UserContextProvider userContext;

    /**
     * Creates a Proposal from a READY_FOR_PROPOSAL Opportunity (status DRAFT). Creates no Sale, Sales
     * Order, Booking, Financial, Payment or Commission data.
     *
     * @param request the proposal data
     * @return 201 Created with the new proposal id and status
     */
    @PostMapping
    public ResponseEntity<ProposalResponse> create(@Valid @RequestBody ProposalCreateRequest request) {
        CreateProposalCommand command = new CreateProposalCommand(
                request.opportunityId(),
                request.responsiblePersonId(),
                request.title(),
                request.notes(),
                request.validUntil(),
                request.commercialTerms());
        UUID id = proposalService.create(
                command, userContext.currentUserId(), canSeeAllOpportunities(), canSeeUnassignedOpportunities());
        return ResponseEntity.created(URI.create("/api/proposals/" + id))
                .body(new ProposalResponse(id, ProposalStatus.DRAFT));
    }

    /**
     * Operational, paginated list of Proposals visible to the caller, with optional filters and search.
     * Terminal-negative Proposals (REJECTED/EXPIRED/CANCELLED) are excluded unless the {@code status} filter
     * explicitly includes them. The contract carries commercial-offer data only — never Sale, Sales Order,
     * Booking, Financial, Payment or Commission data.
     *
     * @param params the optional filters and search term (see {@link ProposalListParams})
     * @param pageable page, size and sort (default: createdAt desc, size 20)
     * @return a page of Proposal list items
     */
    @GetMapping
    public PageResponse<ProposalListItem> list(
            ProposalListParams params,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        boolean unassignedOnly = "unassigned".equalsIgnoreCase(params.responsible());
        UUID responsibleId = (!unassignedOnly
                        && params.responsible() != null
                        && !params.responsible().isBlank())
                ? UUID.fromString(params.responsible())
                : null;
        ProposalSearchCriteria criteria = new ProposalSearchCriteria(
                params.status(),
                responsibleId,
                unassignedOnly,
                params.opportunityId(),
                toStartOfDayUtc(params.createdFrom()),
                toStartOfDayUtc(params.createdTo() != null ? params.createdTo().plusDays(1) : null),
                params.validFrom(),
                params.validTo(),
                params.totalMin(),
                params.totalMax(),
                params.q());
        return PageResponse.from(
                proposalService.list(
                        criteria,
                        pageable,
                        userContext.currentUserId(),
                        canSeeAllProposals(),
                        canSeeUnassignedProposals()),
                item -> item);
    }

    /**
     * Full detail of a Proposal the caller may see, with the source Opportunity kept traceable.
     *
     * @param id the proposal id
     * @return the Proposal detail read model
     */
    @GetMapping("/{id}")
    public ProposalDetail detail(@PathVariable UUID id) {
        return proposalService.detail(
                id, userContext.currentUserId(), canSeeAllProposals(), canSeeUnassignedProposals());
    }

    /**
     * Edits a Draft Proposal's commercial details (validity, terms, payment notes, Proposal-level discount);
     * returns the refreshed detail (with the recomputed subtotal/total). Creates no Financial, Receivable,
     * Payment, Booking or Commission data.
     *
     * @param id the proposal id
     * @param request the new commercial details
     * @return the updated detail
     */
    @PutMapping("/{id}")
    public ProposalDetail update(@PathVariable UUID id, @Valid @RequestBody ProposalUpdateRequest request) {
        UpdateProposalCommand command = new UpdateProposalCommand(
                request.validUntil(),
                request.commercialTerms(),
                request.paymentNotes(),
                request.discountType(),
                request.discountValue());
        return proposalService.updateDetails(
                id, command, userContext.currentUserId(), canSeeAllProposals(), canSeeUnassignedProposals());
    }

    /**
     * Submits a Draft Proposal for review (Draft → Ready for review); returns the refreshed detail. Requires
     * at least one item and a positive total. Creates no Sale, Order, Booking, Financial or Commission data.
     *
     * @param id the proposal id
     * @return the updated detail
     */
    @PostMapping("/{id}/submit")
    public ProposalDetail submit(@PathVariable UUID id) {
        return proposalService.submitForReview(
                id, userContext.currentUserId(), canSeeAllProposals(), canSeeUnassignedProposals());
    }

    /**
     * Approves a Proposal under review (Ready for review → Approved); returns the refreshed detail. Requires
     * the {@code sales:proposal:approve} authority. Does not send the Proposal to the client and creates no
     * Sale, Order, Booking, Financial or Commission data.
     *
     * @param id the proposal id
     * @return the updated detail
     */
    @PostMapping("/{id}/approve")
    public ProposalDetail approve(@PathVariable UUID id) {
        return proposalService.approve(
                id, userContext.currentUserId(), canSeeAllProposals(), canSeeUnassignedProposals());
    }

    /**
     * Rejects a Proposal under review (Ready for review → Rejected) with a reason; returns the refreshed
     * detail. Requires the {@code sales:proposal:approve} authority. Does not send the Proposal to the client
     * and creates no Sale, Order, Booking, Financial or Commission data.
     *
     * @param id the proposal id
     * @param request the rejection reason and optional note
     * @return the updated detail
     */
    @PostMapping("/{id}/reject")
    public ProposalDetail reject(@PathVariable UUID id, @Valid @RequestBody RejectProposalRequest request) {
        return proposalService.reject(
                id,
                request.reason(),
                request.note(),
                userContext.currentUserId(),
                canSeeAllProposals(),
                canSeeUnassignedProposals());
    }

    /**
     * Marks an approved Proposal as sent to the client (Approved → Sent); returns the refreshed detail.
     * Requires the {@code sales:proposal:update} authority. The sending channel is optional descriptive
     * information. Does not trigger any real e-mail/WhatsApp/phone integration, and creates no customer
     * acceptance, Commercial Order, Booking, Financial or Commission data.
     *
     * @param id the proposal id
     * @param request the optional sending channel
     * @return the updated detail
     */
    @PostMapping("/{id}/send")
    public ProposalDetail send(@PathVariable UUID id, @Valid @RequestBody MarkProposalSentRequest request) {
        return proposalService.markAsSent(
                id, request.channel(), userContext.currentUserId(), canSeeAllProposals(), canSeeUnassignedProposals());
    }

    /**
     * Adds an item to a Draft Proposal; returns the refreshed detail (with the recomputed total). Creates
     * no Booking, Financial or Commission data and does not check external availability.
     *
     * @param id the proposal id
     * @param request the item data
     * @return the updated detail
     */
    @PostMapping("/{id}/items")
    public ProposalDetail addItem(@PathVariable UUID id, @Valid @RequestBody ProposalItemRequest request) {
        return proposalService.addItem(
                id, toCommand(request), userContext.currentUserId(), canSeeAllProposals(), canSeeUnassignedProposals());
    }

    /**
     * Updates an item of a Draft Proposal; returns the refreshed detail.
     *
     * @param id the proposal id
     * @param itemId the item id
     * @param request the new item data
     * @return the updated detail
     */
    @PutMapping("/{id}/items/{itemId}")
    public ProposalDetail updateItem(
            @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody ProposalItemRequest request) {
        return proposalService.updateItem(
                id,
                itemId,
                toCommand(request),
                userContext.currentUserId(),
                canSeeAllProposals(),
                canSeeUnassignedProposals());
    }

    /**
     * Removes an item from a Draft Proposal; returns the refreshed detail.
     *
     * @param id the proposal id
     * @param itemId the item id
     * @return the updated detail
     */
    @DeleteMapping("/{id}/items/{itemId}")
    public ProposalDetail removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        return proposalService.removeItem(
                id, itemId, userContext.currentUserId(), canSeeAllProposals(), canSeeUnassignedProposals());
    }

    private static ProposalItemCommand toCommand(ProposalItemRequest r) {
        return new ProposalItemCommand(
                r.type(), r.description(), r.quantity(), r.unitValue(), r.discountType(), r.discountValue());
    }

    // The creation period is given as calendar dates; the column is an instant, so anchor at UTC midnight
    // (the upper bound is pre-incremented by a day by the caller, making the range [from, to) exclusive).
    private static Instant toStartOfDayUtc(LocalDate date) {
        return date != null ? date.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }

    // Source-opportunity visibility for creation reuses the Opportunity read tiers.
    private boolean canSeeAllOpportunities() {
        return userContext.hasScope("crm:opportunity:read:all");
    }

    private boolean canSeeUnassignedOpportunities() {
        return userContext.hasScope("crm:opportunity:read:unassigned");
    }

    // Proposal listing/detail uses the Proposal read tiers.
    private boolean canSeeAllProposals() {
        return userContext.hasScope("sales:proposal:read:all");
    }

    private boolean canSeeUnassignedProposals() {
        return userContext.hasScope("sales:proposal:read:unassigned");
    }
}
