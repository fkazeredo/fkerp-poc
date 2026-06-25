package com.fksoft.erp.domain.commission.service;

import com.fksoft.erp.domain.commission.exception.CommissionAccessDeniedException;
import com.fksoft.erp.domain.commission.exception.CommissionAlreadyExistsException;
import com.fksoft.erp.domain.commission.exception.CommissionNotFoundException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNoAmountException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNoResponsibleException;
import com.fksoft.erp.domain.commission.exception.CommissionOrderNotClosedException;
import com.fksoft.erp.domain.commission.exception.CommissionSourceOrderAccessDeniedException;
import com.fksoft.erp.domain.commission.exception.CommissionSourceOrderNotFoundException;
import com.fksoft.erp.domain.commission.exception.NoApplicableCommissionRuleException;
import com.fksoft.erp.domain.commission.model.Commission;
import com.fksoft.erp.domain.commission.model.CommissionBasis;
import com.fksoft.erp.domain.commission.model.CommissionRule;
import com.fksoft.erp.domain.commission.model.CommissionStatus;
import com.fksoft.erp.domain.commission.model.CommissionTargetType;
import com.fksoft.erp.domain.commission.repository.CommissionRepository;
import com.fksoft.erp.domain.commission.repository.CommissionRuleRepository;
import com.fksoft.erp.domain.commission.service.data.CommissionDetail;
import com.fksoft.erp.domain.commission.service.data.CommissionListItem;
import com.fksoft.erp.domain.commission.service.data.CommissionSearchCriteria;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.repository.ReceivableRepository;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.domain.sales.service.OrderAccessPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        List<Commission> rows = page.getContent();

        Map<UUID, Long> orderNumbers = resolveOrderNumbers(rows.stream().map(Commission::commercialOrderId));
        Map<UUID, String> names = resolveNames(rows.stream().map(Commission::beneficiaryUserId));
        Map<UUID, String> ruleNames = resolveRuleNames(rows.stream().map(Commission::ruleId));
        Map<UUID, String> proposalRefs = resolveProposalRefs(rows.stream().map(Commission::proposalId));
        Map<UUID, String> opportunityRefs = resolveOpportunityRefs(rows.stream().map(Commission::opportunityId));
        Map<UUID, String> receivableStatuses =
                resolveReceivableStatuses(rows.stream().map(Commission::commercialOrderId));

        return page.map(c -> CommissionListItem.from(
                c,
                orderNumbers.getOrDefault(c.commercialOrderId(), 0L),
                names.get(c.beneficiaryUserId()),
                ruleNames.get(c.ruleId()),
                proposalRefs.get(c.proposalId()),
                opportunityRefs.get(c.opportunityId()),
                receivableStatuses.get(c.commercialOrderId())));
    }

    private CommissionDetail toDetail(Commission commission) {
        long orderNumber = orders.findById(commission.commercialOrderId())
                .map(CommercialOrder::number)
                .orElse(0L);
        String beneficiaryName = users.findById(commission.beneficiaryUserId())
                .map(User::username)
                .orElse(null);
        String ruleName =
                rules.findById(commission.ruleId()).map(CommissionRule::name).orElse(null);
        return CommissionDetail.from(commission, orderNumber, beneficiaryName, ruleName);
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
