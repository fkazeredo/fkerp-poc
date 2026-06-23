package com.fksoft.erp.domain.crm.repository;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Read-side aggregate queries for the Opportunity indicators. Every query reuses the caller's visibility
 * {@link Specification} as its WHERE predicate (single source of truth) plus the optional period, so the
 * numbers never include Opportunities the caller cannot see. The volume queries take a period; the
 * pipeline-snapshot queries (active value / value by responsible / overdue) take no period and are
 * restricted to active (non-LOST) Opportunities. Group-by queries return only the buckets present in the
 * data (no N+1, no unbounded loads, §11).
 */
@Component
@RequiredArgsConstructor
public class OpportunityIndicatorQueries {

    private final EntityManager em;

    /**
     * Opportunity counts grouped by pipeline stage (every stage, Lost included). Pass {@code null}
     * bounds for the current snapshot (used to derive the active / ready-for-proposal figures).
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return counts keyed by stage
     */
    public Map<String, Long> countByStage(Specification<Opportunity> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Opportunity> root = q.from(Opportunity.class);
        q.multiselect(root.get("stage"), cb.count(root));
        q.where(where(cb, root, q, visible, from, to));
        q.groupBy(root.get("stage"));
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : em.createQuery(q).getResultList()) {
            result.put(((OpportunityStage) row[0]).name(), (Long) row[1]);
        }
        return result;
    }

    /**
     * Opportunity counts grouped by origin label, busiest first.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return counts keyed by origin label, in busiest-first order
     */
    public Map<String, Long> countByOrigin(Specification<Opportunity> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Opportunity> root = q.from(Opportunity.class);
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
     * Opportunity counts grouped by responsible id (a {@code null} id is the unassigned bucket), busiest
     * first.
     *
     * @param visible the visibility predicate
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return counts keyed by responsible id ({@code null} key = unassigned), busiest-first
     */
    public Map<UUID, Long> countByResponsible(Specification<Opportunity> visible, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Opportunity> root = q.from(Opportunity.class);
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
     * Total estimated value of the active (non-LOST) pipeline — a current snapshot (no period).
     *
     * @param visible the visibility predicate
     * @return the summed estimated value (zero when there is none)
     */
    public BigDecimal sumActivePipelineValue(Specification<Opportunity> visible) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> q = cb.createQuery(BigDecimal.class);
        Root<Opportunity> root = q.from(Opportunity.class);
        q.select(cb.coalesce(cb.sum(root.<BigDecimal>get("estimatedValue")), BigDecimal.ZERO));
        q.where(cb.and(where(cb, root, q, visible, null, null), active(cb, root)));
        return em.createQuery(q).getSingleResult();
    }

    /**
     * Active-pipeline estimated value grouped by responsible id (a {@code null} id is the unassigned
     * bucket) — a current snapshot (no period), highest first.
     *
     * @param visible the visibility predicate
     * @return active estimated value keyed by responsible id ({@code null} key = unassigned)
     */
    public Map<UUID, BigDecimal> sumActiveValueByResponsible(Specification<Opportunity> visible) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Opportunity> root = q.from(Opportunity.class);
        var responsible = root.get("responsiblePersonId");
        var value = cb.coalesce(cb.sum(root.<BigDecimal>get("estimatedValue")), BigDecimal.ZERO);
        q.multiselect(responsible, value);
        q.where(cb.and(where(cb, root, q, visible, null, null), active(cb, root)));
        q.groupBy(responsible);
        q.orderBy(cb.desc(value));
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        for (Object[] row : em.createQuery(q).getResultList()) {
            out.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return out;
    }

    /**
     * Count of active (non-LOST) Opportunities whose expected closing date has passed — a current
     * snapshot (no period).
     *
     * @param visible the visibility predicate
     * @param today the reference date (UTC)
     * @return the count
     */
    public long countActiveOverdueClose(Specification<Opportunity> visible, LocalDate today) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Opportunity> root = q.from(Opportunity.class);
        q.select(cb.count(root));
        q.where(cb.and(
                where(cb, root, q, visible, null, null),
                active(cb, root),
                cb.isNotNull(root.get("expectedCloseDate")),
                cb.lessThan(root.<LocalDate>get("expectedCloseDate"), today)));
        return em.createQuery(q).getSingleResult();
    }

    private static Predicate active(CriteriaBuilder cb, Root<Opportunity> root) {
        return cb.not(root.get("stage").in(List.of("WON", "LOST")));
    }

    private Predicate where(
            CriteriaBuilder cb,
            Root<Opportunity> root,
            CriteriaQuery<?> query,
            Specification<Opportunity> visible,
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
