package com.fksoft.erp.domain.commission.service;

import com.fksoft.erp.domain.commission.exception.CommissionAccessDeniedException;
import com.fksoft.erp.domain.commission.exception.CommissionAlreadyExistsException;
import com.fksoft.erp.domain.commission.exception.CommissionNotFoundException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNoAmountException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNoResponsibleException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNotClosedException;
import com.fksoft.erp.domain.commission.exception.CommissionResolutionReasonNotAvailableException;
import com.fksoft.erp.domain.commission.exception.CommissionSelfApprovalNotAllowedException;
import com.fksoft.erp.domain.commission.exception.CommissionSourceOrderAccessDeniedException;
import com.fksoft.erp.domain.commission.exception.CommissionSourceOrderNotFoundException;
import com.fksoft.erp.domain.commission.exception.NoApplicableCommissionRuleException;
import com.fksoft.erp.domain.commission.model.Commission;
import com.fksoft.erp.domain.commission.model.CommissionBasis;
import com.fksoft.erp.domain.commission.model.CommissionResolutionReason;
import com.fksoft.erp.domain.commission.model.CommissionRule;
import com.fksoft.erp.domain.commission.model.CommissionStatus;
import com.fksoft.erp.domain.commission.model.CommissionTargetType;
import com.fksoft.erp.domain.commission.repository.CommissionRepository;
import com.fksoft.erp.domain.commission.repository.CommissionResolutionReasonRepository;
import com.fksoft.erp.domain.commission.repository.CommissionRuleRepository;
import com.fksoft.erp.domain.commission.service.data.CommissionDetail;
import com.fksoft.erp.domain.commission.service.data.CommissionListItem;
import com.fksoft.erp.domain.commission.service.data.CommissionSearchCriteria;
import com.fksoft.erp.domain.commission.service.data.CommissionStatement;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.financial.exception.PaymentMethodNotAvailableException;
import com.fksoft.erp.domain.financial.model.PaymentMethod;
import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.repository.PaymentMethodRepository;
import com.fksoft.erp.domain.financial.repository.ReceivableRepository;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.reference.ReferenceData;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.domain.sales.service.OrderAccessPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service of Commission Management for generating and reading the Expected Commission. It generates a
 * forecast commission from a commercially-closed Commercial Order using an active Commission Rule, and serves the
 * commission detail. It only <b>reads</b> the Commercial Order and the Receivable (Commission Management does not own
 * them), and it <b>never</b> creates a Commission Payment, Accounts Payable, payroll, tax or accounting data.
 */
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final CommissionRepository commissions;
    private final CommissionRuleRepository rules;
    private final CommissionResolutionReasonRepository resolutionReasons;
    private final PaymentMethodRepository paymentMethods;
    private final ReceivableRepository receivables;
    private final CommercialOrderRepository orders;
    private final ProposalRepository proposals;
    private final OpportunityRepository opportunities;
    private final OrderAccessPolicy orderAccessPolicy;
    private final CommissionAccessPolicy accessPolicy;
    private final UserRepository users;

    /**
     * Generates an {@code EXPECTED} Commission from a commercially-closed Commercial Order the caller may see. The
     * Order must be active (not cancelled), have a commercial responsible and a positive total, and not already have
     * an active Commission. The applied rule is the active, in-window rule for the beneficiary (the Order's
     * commercial responsible), preferring a user-specific rule over a generic {@code COMMERCIAL_RESPONSIBLE} one. The
     * amount is calculated from the received amount when the Order's Receivable already has payments, otherwise from
     * the commercial total (a forecast). Creates no Commission Payment / Accounts Payable / payroll / tax / accounting
     * data and never modifies the Order or the Receivable.
     *
     * @param commercialOrderId the source order id
     * @param userId the authenticated manager generating the commission
     * @param canSeeAllOrders whether the caller holds {@code sales:order:read:all}
     * @param canSeeUnassignedOrders whether the caller may also see the unassigned Order pool
     * @return the new commission id
     * @throws CommissionSourceOrderNotFoundException if the source Order does not exist
     * @throws CommissionSourceOrderAccessDeniedException if the caller may not see the source Order
     * @throws CommissionOrderNotClosedException if the Order is not commercially closed (e.g. cancelled)
     * @throws CommissionOrderNoResponsibleException if the Order has no commercial responsible
     * @throws CommissionOrderNoAmountException if the Order has no positive commercial amount
     * @throws CommissionAlreadyExistsException if the Order already has an active Commission
     * @throws NoApplicableCommissionRuleException if no active in-window rule applies to the beneficiary
     */
    @Transactional
    public UUID generate(UUID commercialOrderId, UUID userId, boolean canSeeAllOrders, boolean canSeeUnassignedOrders) {
        CommercialOrder order =
                orders.findById(commercialOrderId).orElseThrow(CommissionSourceOrderNotFoundException::new);
        if (!orderAccessPolicy.canSee(order, userId, canSeeAllOrders, canSeeUnassignedOrders)) {
            throw new CommissionSourceOrderAccessDeniedException();
        }
        if (!order.status().isActive()) {
            throw new CommissionOrderNotClosedException();
        }
        if (order.responsiblePersonId() == null) {
            throw new CommissionOrderNoResponsibleException();
        }
        if (order.total() == null || order.total().signum() <= 0) {
            throw new CommissionOrderNoAmountException();
        }
        commissions
                .findFirstByCommercialOrderIdAndStatusIn(order.id(), CommissionStatus.active())
                .ifPresent(existing -> {
                    throw new CommissionAlreadyExistsException(existing.id());
                });
        CommissionRule rule = selectRule(order.responsiblePersonId());

        // Use the received-amount basis only when the Order's Receivable already has payments; otherwise the
        // commission stays a forecast off the commercial total.
        Receivable receivable = receivables
                .findFirstByCommercialOrderIdAndStatusIn(order.id(), ReceivableStatus.active())
                .orElse(null);
        boolean received = receivable != null && receivable.amountPaid().signum() > 0;
        CommissionBasis basis = received ? CommissionBasis.RECEIVED_AMOUNT : CommissionBasis.COMMERCIAL_AMOUNT;
        BigDecimal baseAmount = received ? receivable.amountPaid() : order.total();

        Commission commission = Commission.generate(order, rule, basis, baseAmount, userId);
        // If the Receivable is already fully paid at generation time, the commission is eligible right away — the
        // PAID event fired before the commission existed, so the eligibility listener would otherwise miss it.
        if (receivable != null && receivable.status() == ReceivableStatus.PAID) {
            commission.markEligible(receivable.id(), Instant.now());
        }
        commissions.save(commission);
        return commission.id();
    }

    /**
     * Full detail of a Commission the caller may see.
     *
     * @param id the commission id
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Commission
     * @return the detail read model
     * @throws CommissionNotFoundException if the commission does not exist
     * @throws CommissionAccessDeniedException if the caller may not see it
     */
    @Transactional(readOnly = true)
    public CommissionDetail detail(UUID id, UUID userId, boolean canSeeAll) {
        Commission commission = commissions.findById(id).orElseThrow(CommissionNotFoundException::new);
        if (!accessPolicy.canSee(commission, userId, canSeeAll)) {
            throw new CommissionAccessDeniedException();
        }
        return toDetail(commission);
    }

    /**
     * Approves an Eligible Commission the approver may see, making it ready for payment. Enforces the segregation of
     * duties (the beneficiary cannot approve their own commission) and the state invariant (only an Eligible
     * commission can be approved — the entity rejects any other status). Records who approved, when, and the optional
     * notes; registers no payment and creates no Accounts Payable, payroll, tax, accounting or bank data.
     *
     * @param id the commission id
     * @param approverId the authenticated approver
     * @param canSeeAll whether the approver may see every Commission
     * @param notes optional approval notes
     * @return the refreshed commission detail
     * @throws CommissionNotFoundException if the commission does not exist
     * @throws CommissionAccessDeniedException if the approver may not see it
     * @throws CommissionSelfApprovalNotAllowedException if the approver is the beneficiary
     * @throws com.fksoft.erp.domain.commission.exception.CommissionNotEligibleException if it is not Eligible
     */
    @Transactional
    public CommissionDetail approve(UUID id, UUID approverId, boolean canSeeAll, String notes) {
        Commission commission = commissions.findById(id).orElseThrow(CommissionNotFoundException::new);
        if (!accessPolicy.canSee(commission, approverId, canSeeAll)) {
            throw new CommissionAccessDeniedException();
        }
        if (approverId.equals(commission.beneficiaryUserId())) {
            throw new CommissionSelfApprovalNotAllowedException();
        }
        commission.approve(approverId, notes, Instant.now());
        commissions.save(commission);
        return toDetail(commission);
    }

    /**
     * Rejects an Eligible Commission the caller may see (Eligible → Rejected), with a required resolution reason and an
     * optional note, recording who/when. Voiding the commission does NOT touch the source Order or Receivable and
     * creates no refund, payroll, tax or accounting data.
     *
     * @param id the commission id
     * @param reasonId the resolution-reason cadastro id (required, must be active)
     * @param note an optional free-text note
     * @param userId the authenticated user rejecting it
     * @param canSeeAll whether the caller may see every Commission
     * @return the refreshed commission detail
     * @throws CommissionNotFoundException if the commission does not exist
     * @throws CommissionAccessDeniedException if the caller may not see it
     * @throws CommissionResolutionReasonNotAvailableException if the reason is unknown or inactive
     * @throws com.fksoft.erp.domain.commission.exception.CommissionNotRejectableException if it is not Eligible
     */
    @Transactional
    public CommissionDetail reject(UUID id, UUID reasonId, String note, UUID userId, boolean canSeeAll) {
        Commission commission = loadVisible(id, userId, canSeeAll);
        commission.reject(userId, activeReason(reasonId), note, Instant.now());
        commissions.save(commission);
        return toDetail(commission);
    }

    /**
     * Cancels an unpaid Commission the caller may see (Expected/Approved → Cancelled), with a required resolution
     * reason and an optional note, recording who/when. A Paid commission cannot be cancelled through this flow. Voiding
     * the commission does NOT touch the source Order or Receivable and creates no refund, payroll, tax or accounting
     * data.
     *
     * @param id the commission id
     * @param reasonId the resolution-reason cadastro id (required, must be active)
     * @param note an optional free-text note
     * @param userId the authenticated user cancelling it
     * @param canSeeAll whether the caller may see every Commission
     * @return the refreshed commission detail
     * @throws CommissionNotFoundException if the commission does not exist
     * @throws CommissionAccessDeniedException if the caller may not see it
     * @throws CommissionResolutionReasonNotAvailableException if the reason is unknown or inactive
     * @throws com.fksoft.erp.domain.commission.exception.CommissionNotCancellableException if it is not unpaid Expected/Approved
     */
    @Transactional
    public CommissionDetail cancel(UUID id, UUID reasonId, String note, UUID userId, boolean canSeeAll) {
        Commission commission = loadVisible(id, userId, canSeeAll);
        commission.cancel(userId, activeReason(reasonId), note, Instant.now());
        commissions.save(commission);
        return toDetail(commission);
    }

    /**
     * Registers the manual payment of an Approved Commission the caller may see (Approved → Paid), closing the
     * commission cycle. The amount must equal the approved commission amount (full payment only); the payment method
     * must be an active cadastro value. Records the amount, date, method, optional note and who/when. Registering the
     * payment creates NO Accounts Payable, payroll, tax, accounting or bank-transfer data and triggers no bank
     * integration; it never touches the Order or Receivable.
     *
     * @param id the commission id
     * @param amount the paid amount (must equal the commission amount)
     * @param paymentDate the payment date (not in the future)
     * @param paymentMethodId the payment-method cadastro id (must be active)
     * @param note an optional free-text note
     * @param userId the authenticated user registering the payment
     * @param canSeeAll whether the caller may see every Commission
     * @return the refreshed commission detail
     * @throws CommissionNotFoundException if the commission does not exist
     * @throws CommissionAccessDeniedException if the caller may not see it
     * @throws PaymentMethodNotAvailableException if the payment method is unknown or inactive
     * @throws com.fksoft.erp.domain.commission.exception.CommissionNotPayableException if it is not Approved
     * @throws com.fksoft.erp.domain.commission.exception.CommissionPaymentAmountMismatchException if the amount differs
     */
    @Transactional
    public CommissionDetail pay(
            UUID id,
            BigDecimal amount,
            LocalDate paymentDate,
            UUID paymentMethodId,
            String note,
            UUID userId,
            boolean canSeeAll) {
        Commission commission = loadVisible(id, userId, canSeeAll);
        PaymentMethod method = paymentMethods
                .findById(paymentMethodId)
                .filter(ReferenceData::active)
                .orElseThrow(PaymentMethodNotAvailableException::new);
        commission.pay(amount, paymentDate, method, note, userId, Instant.now());
        commissions.save(commission);
        return toDetail(commission);
    }

    private Commission loadVisible(UUID id, UUID userId, boolean canSeeAll) {
        Commission commission = commissions.findById(id).orElseThrow(CommissionNotFoundException::new);
        if (!accessPolicy.canSee(commission, userId, canSeeAll)) {
            throw new CommissionAccessDeniedException();
        }
        return commission;
    }

    private CommissionResolutionReason activeReason(UUID reasonId) {
        return resolutionReasons
                .findById(reasonId)
                .filter(ReferenceData::active)
                .orElseThrow(CommissionResolutionReasonNotAvailableException::new);
    }

    /**
     * Operational, paginated Commission list filtered by the criteria and narrowed by the caller's visibility (own
     * vs all). Resolves the beneficiary / rule names, the source Proposal / Opportunity references and the order's
     * active Receivable status. Carries commission + commercial-origin data only — never payroll, tax, accounting or
     * accounts-payable data.
     *
     * @param criteria the (optional) filters
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Commission
     * @return a page of Commission list items
     */
    @Transactional(readOnly = true)
    public Page<CommissionListItem> list(
            CommissionSearchCriteria criteria, Pageable pageable, UUID userId, boolean canSeeAll) {
        Specification<Commission> spec =
                CommissionSpecifications.matching(criteria).and(accessPolicy.visibleTo(userId, canSeeAll));
        Page<Commission> page = commissions.findAll(spec, pageable);
        return new PageImpl<>(toListItems(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    /**
     * Simple commission statement for one beneficiary over an optional period (by creation date): the visible
     * commission entries plus the per-status totals (expected / eligible / approved / paid). Respects visibility — a
     * caller without {@code commission:read:all} may only request <b>their own</b> statement (else
     * {@code CommissionAccessDeniedException}); the access policy also narrows the query. By default the voided
     * commissions (Rejected / Cancelled) are excluded; pass {@code includeVoided} to add them. Informational only — it
     * approves/pays nothing and carries commission + commercial-origin data only (no payroll, tax or accounting data).
     *
     * @param beneficiaryId the beneficiary whose statement is requested
     * @param from the inclusive period start (by creation date), or {@code null} (all time)
     * @param to the inclusive period end (by creation date), or {@code null} (all time)
     * @param includeVoided whether to include the Rejected/Cancelled commissions
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Commission
     * @return the statement (entries + totals)
     * @throws CommissionAccessDeniedException if an own-tier caller requests another beneficiary's statement
     */
    @Transactional(readOnly = true)
    public CommissionStatement statement(
            UUID beneficiaryId, LocalDate from, LocalDate to, boolean includeVoided, UUID userId, boolean canSeeAll) {
        if (!canSeeAll && !beneficiaryId.equals(userId)) {
            throw new CommissionAccessDeniedException();
        }
        Set<String> statuses = (includeVoided ? EnumSet.allOf(CommissionStatus.class) : CommissionStatus.active())
                .stream().map(Enum::name).collect(Collectors.toSet());
        CommissionSearchCriteria criteria = new CommissionSearchCriteria(
                statuses,
                beneficiaryId,
                null,
                null,
                null,
                toStartOfDayUtc(from),
                to == null ? null : toStartOfDayUtc(to.plusDays(1)),
                null,
                null,
                null,
                null,
                null,
                null);
        Specification<Commission> spec =
                CommissionSpecifications.matching(criteria).and(accessPolicy.visibleTo(userId, canSeeAll));
        List<Commission> rows = commissions.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        String beneficiaryName =
                users.findById(beneficiaryId).map(User::username).orElse(null);
        return CommissionStatement.of(beneficiaryId, beneficiaryName, from, to, toListItems(rows));
    }

    private static Instant toStartOfDayUtc(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    // Builds the list-item read models for a batch of commissions, resolving the cross-aggregate names/references and
    // the source order's Receivable status once per batch (no N+1). Shared by the list and the statement.
    private List<CommissionListItem> toListItems(List<Commission> rows) {
        Map<UUID, Long> orderNumbers = resolveOrderNumbers(rows.stream().map(Commission::commercialOrderId));
        Map<UUID, String> names = resolveNames(rows.stream().map(Commission::beneficiaryUserId));
        Map<UUID, String> ruleNames = resolveRuleNames(rows.stream().map(Commission::ruleId));
        Map<UUID, String> proposalRefs = resolveProposalRefs(rows.stream().map(Commission::proposalId));
        Map<UUID, String> opportunityRefs = resolveOpportunityRefs(rows.stream().map(Commission::opportunityId));
        Map<UUID, String> receivableStatuses =
                resolveReceivableStatuses(rows.stream().map(Commission::commercialOrderId));
        return rows.stream()
                .map(c -> CommissionListItem.from(
                        c,
                        orderNumbers.getOrDefault(c.commercialOrderId(), 0L),
                        names.get(c.beneficiaryUserId()),
                        ruleNames.get(c.ruleId()),
                        proposalRefs.get(c.proposalId()),
                        opportunityRefs.get(c.opportunityId()),
                        receivableStatuses.get(c.commercialOrderId())))
                .toList();
    }

    // Assembles the detail read model: resolves the source order number, the beneficiary / rule / creator names, the
    // source Proposal and Opportunity references and the source order's active Receivable (the related-receivable
    // reference). Keeps the commission's commercial origin traceable; carries no payroll/tax/accounting data.
    private CommissionDetail toDetail(Commission commission) {
        long orderNumber = orders.findById(commission.commercialOrderId())
                .map(CommercialOrder::number)
                .orElse(0L);
        String beneficiaryName = users.findById(commission.beneficiaryUserId())
                .map(User::username)
                .orElse(null);
        String ruleName =
                rules.findById(commission.ruleId()).map(CommissionRule::name).orElse(null);
        String proposalReference =
                proposals.findById(commission.proposalId()).map(Proposal::title).orElse(null);
        String opportunityReference = opportunities
                .findById(commission.opportunityId())
                .map(Opportunity::name)
                .orElse(null);
        String createdByName =
                users.findById(commission.createdBy()).map(User::username).orElse(null);
        String approvedByName = commission.approvedBy() == null
                ? null
                : users.findById(commission.approvedBy()).map(User::username).orElse(null);
        String resolvedByName = commission.resolvedBy() == null
                ? null
                : users.findById(commission.resolvedBy()).map(User::username).orElse(null);
        String resolutionReason = commission.resolutionReason() == null
                ? null
                : commission.resolutionReason().label();
        String paidByName = commission.paidBy() == null
                ? null
                : users.findById(commission.paidBy()).map(User::username).orElse(null);
        String paymentMethod = commission.paymentMethod() == null
                ? null
                : commission.paymentMethod().label();
        Receivable receivable = receivables
                .findFirstByCommercialOrderIdAndStatusIn(commission.commercialOrderId(), ReceivableStatus.active())
                .orElse(null);
        CommissionDetail.Refs refs = new CommissionDetail.Refs(
                beneficiaryName,
                ruleName,
                proposalReference,
                opportunityReference,
                receivable == null ? null : receivable.id(),
                receivable == null ? null : receivable.status().name(),
                createdByName,
                approvedByName,
                resolutionReason,
                resolvedByName,
                paymentMethod,
                paidByName);
        return CommissionDetail.from(commission, orderNumber, refs);
    }

    private Map<UUID, Long> resolveOrderNumbers(Stream<UUID> orderIds) {
        Set<UUID> set = idSet(orderIds);
        return set.isEmpty()
                ? Map.of()
                : orders.findAllById(set).stream()
                        .collect(Collectors.toMap(CommercialOrder::id, CommercialOrder::number));
    }

    private Map<UUID, String> resolveNames(Stream<UUID> ids) {
        Set<UUID> set = idSet(ids);
        return set.isEmpty()
                ? Map.of()
                : users.findAllById(set).stream().collect(Collectors.toMap(User::id, User::username));
    }

    private Map<UUID, String> resolveRuleNames(Stream<UUID> ids) {
        Set<UUID> set = idSet(ids);
        return set.isEmpty()
                ? Map.of()
                : rules.findAllById(set).stream().collect(Collectors.toMap(CommissionRule::id, CommissionRule::name));
    }

    private Map<UUID, String> resolveProposalRefs(Stream<UUID> ids) {
        Set<UUID> set = idSet(ids);
        return set.isEmpty()
                ? Map.of()
                : proposals.findAllById(set).stream().collect(Collectors.toMap(Proposal::id, Proposal::title));
    }

    private Map<UUID, String> resolveOpportunityRefs(Stream<UUID> ids) {
        Set<UUID> set = idSet(ids);
        return set.isEmpty()
                ? Map.of()
                : opportunities.findAllById(set).stream().collect(Collectors.toMap(Opportunity::id, Opportunity::name));
    }

    // The source order's active (non-cancelled) Receivable status, batched by order id (avoids an N+1 per row).
    private Map<UUID, String> resolveReceivableStatuses(Stream<UUID> orderIds) {
        Set<UUID> set = idSet(orderIds);
        if (set.isEmpty()) {
            return Map.of();
        }
        return receivables.findByCommercialOrderIdIn(set).stream()
                .filter(r -> r.status() != ReceivableStatus.CANCELLED)
                .collect(Collectors.toMap(
                        Receivable::commercialOrderId, r -> r.status().name(), (a, b) -> a));
    }

    private static Set<UUID> idSet(Stream<UUID> ids) {
        return ids.filter(Objects::nonNull).collect(Collectors.toSet());
    }

    // Selects the active, in-window Commission Rule for the beneficiary (the Order's commercial responsible),
    // preferring a user-specific rule (targetUserId == beneficiary) over a generic COMMERCIAL_RESPONSIBLE one; among
    // candidates of the same kind the newest wins (the repository already returns them newest-first, so the first
    // match is the newest). SELLER / SALES_REPRESENTATIVE generic rules are not auto-matched (there is no user-role
    // model yet).
    private CommissionRule selectRule(UUID beneficiary) {
        LocalDate today = LocalDate.now();
        List<CommissionRule> active = rules.findByActiveTrueOrderByCreatedAtDesc().stream()
                .filter(rule -> inWindow(rule, today))
                .toList();
        return active.stream()
                .filter(rule -> beneficiary.equals(rule.targetUserId()))
                .findFirst()
                .or(() -> active.stream()
                        .filter(rule -> rule.targetUserId() == null
                                && rule.targetType() == CommissionTargetType.COMMERCIAL_RESPONSIBLE)
                        .findFirst())
                .orElseThrow(NoApplicableCommissionRuleException::new);
    }

    private static boolean inWindow(CommissionRule rule, LocalDate today) {
        boolean started = !today.isBefore(rule.startDate());
        boolean notEnded = rule.endDate() == null || !today.isAfter(rule.endDate());
        return started && notEnded;
    }
}
