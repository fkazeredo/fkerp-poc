package com.fksoft.erp.domain.financial.service;

import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.repository.CustomerRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.service.CustomerService;
import com.fksoft.erp.domain.financial.exception.InstallmentNotPayableException;
import com.fksoft.erp.domain.financial.exception.OrderBookingNotConfirmedException;
import com.fksoft.erp.domain.financial.exception.PaymentAlreadyReversedException;
import com.fksoft.erp.domain.financial.exception.PaymentExceedsOutstandingException;
import com.fksoft.erp.domain.financial.exception.PaymentInstallmentNotFoundException;
import com.fksoft.erp.domain.financial.exception.PaymentMethodNotAvailableException;
import com.fksoft.erp.domain.financial.exception.PaymentNotFoundException;
import com.fksoft.erp.domain.financial.exception.ReceivableAccessDeniedException;
import com.fksoft.erp.domain.financial.exception.ReceivableAlreadyExistsException;
import com.fksoft.erp.domain.financial.exception.ReceivableNotFoundException;
import com.fksoft.erp.domain.financial.exception.SourceOrderAccessDeniedException;
import com.fksoft.erp.domain.financial.exception.SourceOrderNotFoundException;
import com.fksoft.erp.domain.financial.model.PaymentMethod;
import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableCreated;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.model.ReceivableStatusChanged;
import com.fksoft.erp.domain.financial.repository.PaymentMethodRepository;
import com.fksoft.erp.domain.financial.repository.ReceivableRepository;
import com.fksoft.erp.domain.financial.service.data.CreateReceivableCommand;
import com.fksoft.erp.domain.financial.service.data.EligibleOrder;
import com.fksoft.erp.domain.financial.service.data.ReceivableDetail;
import com.fksoft.erp.domain.financial.service.data.ReceivableListItem;
import com.fksoft.erp.domain.financial.service.data.ReceivableSearchCriteria;
import com.fksoft.erp.domain.financial.service.data.RegisterPaymentCommand;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.reference.ReferenceData;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.domain.sales.service.OrderAccessPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service of Financial Operations: creates a Receivable from a Commercial Order with a CONFIRMED
 * booking and serves the operational list/detail and the eligible-orders selector. One service per area
 * handles command and reads. It never registers a Payment, nor creates Commission, Invoice, Booking or
 * Customer Care data, and never takes ownership of the Commercial Order or the Customer (it only reads them).
 */
@Service
@RequiredArgsConstructor
public class ReceivableService {

    private final ReceivableRepository receivables;
    private final PaymentMethodRepository paymentMethods;
    private final ReceivableAccessPolicy accessPolicy;
    private final CommercialOrderRepository orders;
    private final OrderAccessPolicy orderAccessPolicy;
    private final CustomerService customerService;
    private final CustomerRepository customers;
    private final ProposalRepository proposals;
    private final OpportunityRepository opportunities;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    /**
     * Creates a Receivable from a confirmed Commercial Order the caller may see. The Order must have a
     * CONFIRMED booking and at most one active Receivable. The Receivable preserves the commercial origin and
     * total and starts OPEN. Registers no Payment and creates no Commission/Invoice/Booking/Customer Care data.
     *
     * @param cmd the receivable data (source order id, due date, optional notes/financial responsible)
     * @param userId the authenticated financial user
     * @param canSeeAllOrders whether the caller holds {@code sales:order:read:all}
     * @param canSeeUnassignedOrders whether the caller may also see the unassigned Order pool
     * @return the new receivable id
     * @throws SourceOrderNotFoundException if the source Order does not exist
     * @throws SourceOrderAccessDeniedException if the caller may not see the source Order
     * @throws OrderBookingNotConfirmedException if the Order's booking is not CONFIRMED
     * @throws ReceivableAlreadyExistsException if the Order already has an active Receivable
     */
    @Transactional
    public UUID create(
            CreateReceivableCommand cmd, UUID userId, boolean canSeeAllOrders, boolean canSeeUnassignedOrders) {
        CommercialOrder order = orders.findById(cmd.commercialOrderId()).orElseThrow(SourceOrderNotFoundException::new);
        if (!orderAccessPolicy.canSee(order, userId, canSeeAllOrders, canSeeUnassignedOrders)) {
            throw new SourceOrderAccessDeniedException();
        }
        if (!"CONFIRMED".equals(order.bookingStatus())) {
            throw new OrderBookingNotConfirmedException();
        }
        receivables
                .findFirstByCommercialOrderIdAndStatusIn(order.id(), ReceivableStatus.active())
                .ifPresent(existing -> {
                    throw new ReceivableAlreadyExistsException(existing.id());
                });
        // The Customer normally already exists (materialized at Order creation); resolve defensively.
        Customer customer = customerService.findOrCreateFromLead(order.leadId(), userId);
        Receivable receivable = Receivable.createFromOrder(
                order,
                customer,
                cmd.dueDate(),
                cmd.paymentNotes(),
                cmd.financialResponsiblePersonId(),
                cmd.installments(),
                userId);
        receivables.save(receivable);
        events.publishEvent(new ReceivableCreated(receivable.id(), order.id(), customer.id(), userId));
        events.publishEvent(new ReceivableStatusChanged(
                receivable.id(), order.id(), receivable.status().name()));
        return receivable.id();
    }

    /**
     * Operational, paginated Receivable list filtered by the criteria and the caller's visibility.
     *
     * @param criteria the (optional) filters
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Receivable
     * @return a page of operational Receivable items
     */
    @Transactional(readOnly = true)
    public Page<ReceivableListItem> list(
            ReceivableSearchCriteria criteria, Pageable pageable, UUID userId, boolean canSeeAll) {
        Specification<Receivable> spec =
                ReceivableSpecifications.matching(criteria).and(accessPolicy.visibleTo(userId, canSeeAll));
        Page<Receivable> page = receivables.findAll(spec, pageable);

        Map<UUID, Long> orderNumbers =
                resolveOrderNumbers(page.getContent().stream().map(Receivable::commercialOrderId));
        Map<UUID, String> customerNames =
                resolveCustomerNames(page.getContent().stream().map(Receivable::customerId));
        Map<UUID, String> names = resolveNames(page.getContent().stream()
                .flatMap(r -> Stream.of(r.commercialResponsiblePersonId(), r.financialResponsiblePersonId())));

        return page.map(r -> ReceivableListItem.from(
                r,
                orderNumbers.getOrDefault(r.commercialOrderId(), 0L),
                customerNames.get(r.customerId()),
                nameOrNull(names, r.commercialResponsiblePersonId()),
                nameOrNull(names, r.financialResponsiblePersonId())));
    }

    /**
     * Full detail of a Receivable the caller may see.
     *
     * @param id the receivable id
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Receivable
     * @return the detail read model
     * @throws ReceivableNotFoundException if the Receivable does not exist
     * @throws ReceivableAccessDeniedException if the caller may not see it
     */
    @Transactional(readOnly = true)
    public ReceivableDetail detail(UUID id, UUID userId, boolean canSeeAll) {
        Receivable receivable = receivables.findById(id).orElseThrow(ReceivableNotFoundException::new);
        if (!accessPolicy.canSee(receivable, userId, canSeeAll)) {
            throw new ReceivableAccessDeniedException();
        }
        return toDetail(receivable);
    }

    /**
     * Registers a full payment for one installment of a Receivable the caller may see. The payment amount must
     * equal the installment's amount (this slice registers full payments only). The installment becomes PAID and,
     * when every installment is paid, the Receivable becomes PAID (otherwise PARTIALLY_PAID). Records no
     * Commission, Invoice or bank-reconciliation data and never touches the source Order, Lead or Customer.
     *
     * @param receivableId the receivable id
     * @param installmentId the target installment id
     * @param cmd the payment data (method, amount, date, optional note)
     * @param userId the authenticated financial user registering the payment
     * @param canSeeAll whether the caller may see every Receivable
     * @return the refreshed Receivable detail
     * @throws ReceivableNotFoundException if the Receivable does not exist
     * @throws ReceivableAccessDeniedException if the caller may not see it
     * @throws PaymentMethodNotAvailableException if the payment method is unknown or inactive
     * @throws PaymentInstallmentNotFoundException if the installment is not part of the Receivable
     * @throws InstallmentNotPayableException if the installment is already paid or cancelled
     * @throws PaymentExceedsOutstandingException if the amount exceeds the installment's outstanding amount
     */
    @Transactional
    public ReceivableDetail registerPayment(
            UUID receivableId, UUID installmentId, RegisterPaymentCommand cmd, UUID userId, boolean canSeeAll) {
        Receivable receivable = receivables.findById(receivableId).orElseThrow(ReceivableNotFoundException::new);
        if (!accessPolicy.canSee(receivable, userId, canSeeAll)) {
            throw new ReceivableAccessDeniedException();
        }
        PaymentMethod method = paymentMethods
                .findById(cmd.paymentMethodId())
                .filter(ReferenceData::active)
                .orElseThrow(PaymentMethodNotAvailableException::new);
        receivable.registerPayment(installmentId, cmd.amount(), cmd.paymentDate(), method, cmd.note(), userId);
        receivables.save(receivable);
        events.publishEvent(new ReceivableStatusChanged(
                receivable.id(),
                receivable.commercialOrderId(),
                receivable.status().name()));
        return toDetail(receivable);
    }

    /**
     * Reverses a registered payment of a Receivable the caller may see (a payment-entry correction). The payment
     * is marked reversed (kept in history, never deleted), the settled installment and the Receivable's paid total
     * and status are re-consolidated (a {@code PAID} receivable returns to {@code PARTIALLY_PAID}/{@code OPEN}), and
     * the financial-status reflection onto the Order is republished. Creates no refund, Commission or Customer Care
     * data and never touches the source Order, Lead or Customer.
     *
     * @param receivableId the receivable id
     * @param paymentId the payment to reverse
     * @param reason the reason for the reversal (required)
     * @param userId the authenticated financial user reversing the payment
     * @param canSeeAll whether the caller may see every Receivable
     * @return the refreshed Receivable detail
     * @throws ReceivableNotFoundException if the Receivable does not exist
     * @throws ReceivableAccessDeniedException if the caller may not see it
     * @throws PaymentNotFoundException if the payment is not part of the Receivable
     * @throws PaymentAlreadyReversedException if the payment has already been reversed
     */
    @Transactional
    public ReceivableDetail reversePayment(
            UUID receivableId, UUID paymentId, String reason, UUID userId, boolean canSeeAll) {
        Receivable receivable = receivables.findById(receivableId).orElseThrow(ReceivableNotFoundException::new);
        if (!accessPolicy.canSee(receivable, userId, canSeeAll)) {
            throw new ReceivableAccessDeniedException();
        }
        receivable.reversePayment(paymentId, reason, userId, Instant.now());
        receivables.save(receivable);
        events.publishEvent(new ReceivableStatusChanged(
                receivable.id(),
                receivable.commercialOrderId(),
                receivable.status().name()));
        return toDetail(receivable);
    }

    // Assembles the detail read model: resolves the order number, payer/commercial references and the
    // responsible / creator / payment-registrant names, then maps the aggregate. Called by detail() and
    // registerPayment() (both inside a transaction so the installments and payments are loaded).
    private ReceivableDetail toDetail(Receivable receivable) {
        long orderNumber = orders.findById(receivable.commercialOrderId())
                .map(CommercialOrder::number)
                .orElse(0L);
        String customerName =
                customers.findById(receivable.customerId()).map(Customer::name).orElse(null);
        String proposalReference =
                proposals.findById(receivable.proposalId()).map(Proposal::title).orElse(null);
        String opportunityReference = opportunities
                .findById(receivable.opportunityId())
                .map(Opportunity::name)
                .orElse(null);
        Map<UUID, String> names = resolveNames(Stream.concat(
                Stream.of(
                        receivable.commercialResponsiblePersonId(),
                        receivable.financialResponsiblePersonId(),
                        receivable.createdBy()),
                receivable.payments().stream().flatMap(p -> Stream.of(p.registeredBy(), p.reversedBy()))));
        return ReceivableDetail.from(
                receivable, orderNumber, customerName, proposalReference, opportunityReference, names, LocalDate.now());
    }

    /**
     * The Commercial Orders eligible to originate a Receivable: booking CONFIRMED, without an active
     * Receivable, and visible to the caller.
     *
     * @param userId the calling user
     * @param canSeeAllOrders whether the caller holds {@code sales:order:read:all}
     * @param canSeeUnassignedOrders whether the caller may also see the unassigned Order pool
     * @return the eligible orders for the create form's selector
     */
    @Transactional(readOnly = true)
    public List<EligibleOrder> eligibleOrders(UUID userId, boolean canSeeAllOrders, boolean canSeeUnassignedOrders) {
        Specification<CommercialOrder> spec = ((Specification<CommercialOrder>)
                        (root, query, cb) -> cb.equal(root.get("bookingStatus"), "CONFIRMED"))
                .and(orderAccessPolicy.visibleTo(userId, canSeeAllOrders, canSeeUnassignedOrders));
        Set<UUID> withReceivable = new HashSet<>(receivables.findOrderIdsWithActiveReceivable());
        List<CommercialOrder> eligible = orders.findAll(spec).stream()
                .filter(order -> !withReceivable.contains(order.id()))
                .toList();
        Map<UUID, String> customerNames =
                resolveCustomerNamesByLead(eligible.stream().map(CommercialOrder::leadId));
        return eligible.stream()
                .map(order ->
                        new EligibleOrder(order.id(), order.number(), customerNames.get(order.leadId()), order.total()))
                .toList();
    }

    private Map<UUID, Long> resolveOrderNumbers(Stream<UUID> orderIds) {
        Set<UUID> set = orderIds.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : orders.findAllById(set).stream()
                        .collect(Collectors.toMap(CommercialOrder::id, CommercialOrder::number));
    }

    private Map<UUID, String> resolveCustomerNames(Stream<UUID> customerIds) {
        Set<UUID> set = customerIds.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : customers.findAllById(set).stream().collect(Collectors.toMap(Customer::id, Customer::name));
    }

    private Map<UUID, String> resolveCustomerNamesByLead(Stream<UUID> leadIds) {
        Set<UUID> set = leadIds.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : customers.findByLeadIdIn(set).stream().collect(Collectors.toMap(Customer::leadId, Customer::name));
    }

    private Map<UUID, String> resolveNames(Stream<UUID> ids) {
        Set<UUID> set = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : users.findAllById(set).stream().collect(Collectors.toMap(User::id, User::username));
    }

    // Null-safe lookup: the resolved maps may be the immutable empty map, which rejects a null key.
    private static String nameOrNull(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }
}
