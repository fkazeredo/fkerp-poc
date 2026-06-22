package com.fksoft.erp.domain.booking.repository;

import com.fksoft.erp.domain.booking.model.BookingItem;
import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Read-side aggregate queries for the Booking Operations indicators. Every query reuses the caller's visibility
 * {@link Specification} as its WHERE predicate (single source of truth) plus the optional period (by the
 * request's {@code createdAt}), so the numbers never include Booking Requests the caller cannot see. A
 * {@code null} period bound is omitted — passing both as {@code null} yields the current snapshot (used for the
 * ready-for-Finance figure). Group-by queries return only the buckets present in the data; the average is
 * computed over the (bounded) confirmed-in-period set. Exposes operational reservation figures only — never
 * Financial, Payment or Commission data.
 */
@Component
@RequiredArgsConstructor
public class BookingIndicatorQueries {

    private final EntityManager em;

    /**
     * Booking Request counts grouped by status. Pass {@code null} bounds for the current snapshot (used to derive
     * the total and the ready-for-Finance figure).
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return counts keyed by status
     */
    public Map<String, Long> countByStatus(Specification<BookingRequest> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<BookingRequest> root = q.from(BookingRequest.class);
        q.multiselect(root.get("status"), cb.count(root));
        q.where(where(cb, root, q, visible, from, to));
        q.groupBy(root.get("status"));
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : em.createQuery(q).getResultList()) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }

    /**
     * Booking item counts grouped by item type, over the requests created in the period.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return item counts keyed by type
     */
    public Map<ProposalItemType, Long> countItemsByType(
            Specification<BookingRequest> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<BookingRequest> root = q.from(BookingRequest.class);
        Join<BookingRequest, BookingItem> items = root.join("items");
        q.multiselect(items.get("type"), cb.count(items));
        q.where(where(cb, root, q, visible, from, to));
        q.groupBy(items.get("type"));
        Map<ProposalItemType, Long> result = new EnumMap<>(ProposalItemType.class);
        for (Object[] row : em.createQuery(q).getResultList()) {
            result.put((ProposalItemType) row[0], (Long) row[1]);
        }
        return result;
    }

    /**
     * How many booking items are failed, over the requests created in the period.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return the failed-item count
     */
    public long countFailedItems(Specification<BookingRequest> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<BookingRequest> root = q.from(BookingRequest.class);
        Join<BookingRequest, BookingItem> items = root.join("items");
        q.select(cb.count(items));
        Predicate base = where(cb, root, q, visible, from, to);
        q.where(cb.and(base, cb.equal(items.get("status"), "FAILED")));
        return em.createQuery(q).getSingleResult();
    }

    /**
     * The average time, in seconds, from a Booking Request's creation to its confirmation, over the visible
     * requests confirmed within the period (those with a {@code confirmedAt}). Computed in Java over the bounded
     * confirmed-in-period set, so the visibility predicate is reused as-is for every tier.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return the average creation→confirmation time in seconds, or {@code null} when there is no confirmed data
     */
    public Long avgConfirmationSeconds(Specification<BookingRequest> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<BookingRequest> root = q.from(BookingRequest.class);
        q.multiselect(root.get("createdAt"), root.get("confirmedAt"));
        Predicate base = where(cb, root, q, visible, from, to);
        q.where(cb.and(base, cb.equal(root.get("status"), "CONFIRMED"), cb.isNotNull(root.get("confirmedAt"))));
        List<Object[]> rows = em.createQuery(q).getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        long totalSeconds = 0;
        for (Object[] row : rows) {
            totalSeconds += Duration.between((Instant) row[0], (Instant) row[1]).getSeconds();
        }
        return totalSeconds / rows.size();
    }

    private Predicate where(
            CriteriaBuilder cb,
            Root<BookingRequest> root,
            CriteriaQuery<?> query,
            Specification<BookingRequest> visible,
            Instant from,
            Instant to) {
        List<Predicate> predicates = new ArrayList<>();
        Predicate visibility = visible.toPredicate(root, query, cb);
        if (visibility != null) {
            predicates.add(visibility);
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThan(root.<Instant>get("createdAt"), to));
        }
        return cb.and(predicates.toArray(Predicate[]::new));
    }
}
