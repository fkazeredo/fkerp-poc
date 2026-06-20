package com.fksoft.erp.domain.sales.repository;

import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Read-side aggregate queries for the Proposal indicators. Every query reuses the caller's visibility
 * {@link Specification} as its WHERE predicate (single source of truth) plus the optional period, so the
 * numbers never include Proposals the caller cannot see. A {@code null} period bound is omitted — passing
 * both as {@code null} yields the current snapshot (used for the waiting-for-review / waiting-for-decision
 * figures). Group-by queries return only the buckets present in the data (no N+1, no unbounded loads, §11).
 */
@Component
@RequiredArgsConstructor
public class ProposalIndicatorQueries {

    private final EntityManager em;

    /**
     * Proposal counts grouped by lifecycle status. Pass {@code null} bounds for the current snapshot.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return counts keyed by status
     */
    public Map<ProposalStatus, Long> countByStatus(Specification<Proposal> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Proposal> root = q.from(Proposal.class);
        q.multiselect(root.get("status"), cb.count(root));
        q.where(where(cb, root, q, visible, from, to));
        q.groupBy(root.get("status"));
        Map<ProposalStatus, Long> result = new EnumMap<>(ProposalStatus.class);
        for (Object[] row : em.createQuery(q).getResultList()) {
            result.put((ProposalStatus) row[0], (Long) row[1]);
        }
        return result;
    }

    /**
     * Summed Proposal {@code total} grouped by lifecycle status, over the period.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return summed total keyed by status (zero buckets are simply absent)
     */
    public Map<ProposalStatus, BigDecimal> sumTotalByStatus(Specification<Proposal> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Proposal> root = q.from(Proposal.class);
        q.multiselect(root.get("status"), cb.coalesce(cb.sum(root.<BigDecimal>get("total")), BigDecimal.ZERO));
        q.where(where(cb, root, q, visible, from, to));
        q.groupBy(root.get("status"));
        Map<ProposalStatus, BigDecimal> result = new EnumMap<>(ProposalStatus.class);
        for (Object[] row : em.createQuery(q).getResultList()) {
            result.put((ProposalStatus) row[0], (BigDecimal) row[1]);
        }
        return result;
    }

    /**
     * Proposal counts grouped by responsible id (a {@code null} id is the unassigned bucket), busiest
     * first, over the period.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return counts keyed by responsible id ({@code null} key = unassigned), busiest-first
     */
    public Map<UUID, Long> countByResponsible(Specification<Proposal> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Proposal> root = q.from(Proposal.class);
        var responsible = root.get("responsiblePersonId");
        q.multiselect(responsible, cb.count(root));
        q.where(where(cb, root, q, visible, from, to));
        q.groupBy(responsible);
        q.orderBy(cb.desc(cb.count(root)));
        Map<UUID, Long> out = new LinkedHashMap<>();
        for (Object[] row : em.createQuery(q).getResultList()) {
            out.put((UUID) row[0], (Long) row[1]);
        }
        return out;
    }

    private Predicate where(
            CriteriaBuilder cb,
            Root<Proposal> root,
            CriteriaQuery<?> query,
            Specification<Proposal> visible,
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
