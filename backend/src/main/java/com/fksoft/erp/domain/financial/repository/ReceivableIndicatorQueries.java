package com.fksoft.erp.domain.financial.repository;

import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivablePayment;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.service.data.ReceivableIndicators.MethodTotal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Read-side aggregate queries for the Financial Operations indicators. Every query reuses the caller's
 * visibility {@link Specification} as its WHERE predicate (single source of truth), so the numbers never include
 * Receivables the caller cannot see. The snapshot queries (counts, outstanding) ignore the period; the
 * payment-based queries restrict by the payment date and count only the <b>non-reversed</b> payments (a reversed
 * payment is a correction, not money received). Exposes receivable + received-payment figures only — <b>never</b>
 * Commission, Accounts Payable, bank-reconciliation, accounting or fiscal data.
 */
@Component
@RequiredArgsConstructor
public class ReceivableIndicatorQueries {

    private static final int SCALE = 2;

    private final EntityManager em;

    /**
     * Current Receivable counts grouped by status (a snapshot — ignores any period).
     *
     * @param visible the visibility predicate
     * @return counts keyed by status name
     */
    public Map<String, Long> countByStatus(Specification<Receivable> visible) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Receivable> root = q.from(Receivable.class);
        q.multiselect(root.get("status"), cb.count(root));
        Predicate visibility = visible.toPredicate(root, q, cb);
        if (visibility != null) {
            q.where(visibility);
        }
        q.groupBy(root.get("status"));
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : em.createQuery(q).getResultList()) {
            result.put(((ReceivableStatus) row[0]).name(), (Long) row[1]);
        }
        return result;
    }

    /**
     * The current outstanding total — Σ({@code total_amount − amount_paid}) over the visible Receivables that are
     * not {@code CANCELLED} (a {@code PAID} receivable contributes zero). A snapshot — ignores any period.
     *
     * @param visible the visibility predicate
     * @return the outstanding amount (scale 2, never {@code null})
     */
    public BigDecimal sumOutstanding(Specification<Receivable> visible) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> q = cb.createQuery(BigDecimal.class);
        Root<Receivable> root = q.from(Receivable.class);
        q.select(cb.sum(cb.diff(root.get("totalAmount"), root.get("amountPaid"))));
        List<Predicate> predicates = new ArrayList<>();
        Predicate visibility = visible.toPredicate(root, q, cb);
        if (visibility != null) {
            predicates.add(visibility);
        }
        predicates.add(cb.notEqual(root.get("status"), ReceivableStatus.CANCELLED));
        q.where(cb.and(predicates.toArray(Predicate[]::new)));
        BigDecimal sum = em.createQuery(q).getSingleResult();
        return sum == null ? zero() : sum.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * How many Receivables were settled ({@code PAID}) with their last payment date in the period.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on the last payment date (or {@code null})
     * @param to inclusive upper bound on the last payment date (or {@code null})
     * @return the paid-in-period count
     */
    public long countPaidReceivablesInPeriod(Specification<Receivable> visible, LocalDate from, LocalDate to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Receivable> root = q.from(Receivable.class);
        q.select(cb.count(root));
        List<Predicate> predicates = new ArrayList<>();
        Predicate visibility = visible.toPredicate(root, q, cb);
        if (visibility != null) {
            predicates.add(visibility);
        }
        predicates.add(cb.equal(root.get("status"), ReceivableStatus.PAID));
        addDatePeriod(cb, predicates, root.get("lastPaymentDate"), from, to);
        q.where(cb.and(predicates.toArray(Predicate[]::new)));
        return em.createQuery(q).getSingleResult();
    }

    /**
     * How many effective (non-reversed) payments were received in the period over the visible Receivables.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on the payment date (or {@code null})
     * @param to inclusive upper bound on the payment date (or {@code null})
     * @return the count of non-reversed payments
     */
    public long countPayments(Specification<Receivable> visible, LocalDate from, LocalDate to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Receivable> root = q.from(Receivable.class);
        Join<Receivable, ReceivablePayment> payments = root.join("payments");
        q.select(cb.count(payments));
        q.where(paymentWhere(cb, root, q, payments, visible, from, to));
        return em.createQuery(q).getSingleResult();
    }

    /**
     * The total amount received (non-reversed payments) in the period over the visible Receivables.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on the payment date (or {@code null})
     * @param to inclusive upper bound on the payment date (or {@code null})
     * @return the received amount (scale 2, never {@code null})
     */
    public BigDecimal sumReceived(Specification<Receivable> visible, LocalDate from, LocalDate to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> q = cb.createQuery(BigDecimal.class);
        Root<Receivable> root = q.from(Receivable.class);
        Join<Receivable, ReceivablePayment> payments = root.join("payments");
        q.select(cb.sum(payments.get("amount")));
        q.where(paymentWhere(cb, root, q, payments, visible, from, to));
        BigDecimal sum = em.createQuery(q).getSingleResult();
        return sum == null ? zero() : sum.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The received (non-reversed) payments of the period grouped by payment method, with the per-method count and
     * total.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on the payment date (or {@code null})
     * @param to inclusive upper bound on the payment date (or {@code null})
     * @return the per-method totals
     */
    public List<MethodTotal> paymentsByMethod(Specification<Receivable> visible, LocalDate from, LocalDate to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Receivable> root = q.from(Receivable.class);
        Join<Receivable, ReceivablePayment> payments = root.join("payments");
        var method = payments.join("method");
        q.multiselect(method.get("code"), method.get("label"), cb.count(payments), cb.sum(payments.get("amount")));
        q.where(paymentWhere(cb, root, q, payments, visible, from, to));
        q.groupBy(method.get("code"), method.get("label"));
        List<MethodTotal> result = new ArrayList<>();
        for (Object[] row : em.createQuery(q).getResultList()) {
            BigDecimal amount = (BigDecimal) row[3];
            result.add(new MethodTotal(
                    (String) row[0],
                    (String) row[1],
                    (Long) row[2],
                    amount == null ? zero() : amount.setScale(SCALE, RoundingMode.HALF_UP)));
        }
        return result;
    }

    // The common WHERE for the payment-based queries: visibility (on the Receivable) + non-reversed + the payment
    // date in the period.
    private Predicate paymentWhere(
            CriteriaBuilder cb,
            Root<Receivable> root,
            CriteriaQuery<?> query,
            Join<Receivable, ReceivablePayment> payments,
            Specification<Receivable> visible,
            LocalDate from,
            LocalDate to) {
        List<Predicate> predicates = new ArrayList<>();
        Predicate visibility = visible.toPredicate(root, query, cb);
        if (visibility != null) {
            predicates.add(visibility);
        }
        predicates.add(cb.isNull(payments.get("reversedAt")));
        addDatePeriod(cb, predicates, payments.get("paymentDate"), from, to);
        return cb.and(predicates.toArray(Predicate[]::new));
    }

    private void addDatePeriod(
            CriteriaBuilder cb,
            List<Predicate> predicates,
            jakarta.persistence.criteria.Path<LocalDate> date,
            LocalDate from,
            LocalDate to) {
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(date, from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(date, to));
        }
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
