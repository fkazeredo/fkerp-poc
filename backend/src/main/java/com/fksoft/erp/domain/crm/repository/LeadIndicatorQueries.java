package com.fksoft.erp.domain.crm.repository;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadInteraction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Read-side aggregate queries for the Lead indicators. Every query reuses the caller's visibility
 * {@link Specification} as its WHERE predicate (single source of truth) plus the optional period, so
 * the numbers never include Leads the caller cannot see. Group-by queries return only the buckets
 * present in the data (no N+1, no unbounded loads, §11).
 */
@Component
@RequiredArgsConstructor
public class LeadIndicatorQueries {

    private final EntityManager em;

    /**
     * Lead counts grouped by status (every status, Lost included).
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return counts keyed by status
     */
    public Map<String, Long> countByStatus(Specification<Lead> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Lead> root = q.from(Lead.class);
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
     * Lead counts grouped by origin label, busiest first.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return counts keyed by origin label, in busiest-first order
     */
    public Map<String, Long> countByOrigin(Specification<Lead> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Lead> root = q.from(Lead.class);
        var label = root.get("origin").get("label");
        q.multiselect(label, cb.count(root));
        q.where(where(cb, root, q, visible, from, to));
        q.groupBy(label);
        q.orderBy(cb.desc(cb.count(root)));
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object[] row : em.createQuery(q).getResultList()) {
            out.put((String) row[0], (Long) row[1]);
        }
        return out;
    }

    /**
     * Lead counts grouped by responsible id (a {@code null} id is the unassigned bucket), busiest first.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return counts keyed by responsible id ({@code null} key = unassigned), busiest-first
     */
    public Map<UUID, Long> countByResponsible(Specification<Lead> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Lead> root = q.from(Lead.class);
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

    /**
     * Count of NEW Leads that have no interaction yet (waiting for first contact).
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return the count
     */
    public long countWaitingFirstContact(Specification<Lead> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Lead> root = q.from(Lead.class);
        Subquery<UUID> interactions = q.subquery(UUID.class);
        var li = interactions.from(LeadInteraction.class);
        interactions.select(li.get("leadId")).where(cb.equal(li.get("leadId"), root.get("id")));
        q.select(cb.count(root));
        q.where(cb.and(
                where(cb, root, q, visible, from, to),
                cb.equal(root.get("status"), "NEW"),
                cb.not(cb.exists(interactions))));
        return em.createQuery(q).getSingleResult();
    }

    private Predicate where(
            CriteriaBuilder cb,
            Root<Lead> root,
            CriteriaQuery<?> query,
            Specification<Lead> visible,
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
