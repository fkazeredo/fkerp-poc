package com.fksoft.erp.application.read;

import com.fksoft.erp.application.api.dto.LeadDetailResponse;
import com.fksoft.erp.application.api.dto.LeadIndicatorsResponse;
import com.fksoft.erp.application.api.dto.LeadListItemResponse;
import com.fksoft.erp.application.api.dto.PendingLeadResponse;
import com.fksoft.erp.application.api.dto.ResponsibleResponse;
import com.fksoft.erp.domain.crm.LatestInteractionRow;
import com.fksoft.erp.domain.crm.Lead;
import com.fksoft.erp.domain.crm.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.LeadAccessPolicy;
import com.fksoft.erp.domain.crm.LeadIndicatorQueries;
import com.fksoft.erp.domain.crm.LeadNotFoundException;
import com.fksoft.erp.domain.crm.LeadPendingSpecifications;
import com.fksoft.erp.domain.crm.LeadRepository;
import com.fksoft.erp.domain.crm.LeadSearchCriteria;
import com.fksoft.erp.domain.crm.LeadSpecifications;
import com.fksoft.erp.domain.crm.LeadStatus;
import com.fksoft.erp.domain.crm.PendingLeadReasons;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.time.Instant;
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
 * Read side of the Commercial / CRM module (queries only). It orchestrates the domain repositories,
 * access policy, specifications and aggregate queries, and assembles the transport {@code *Response}
 * DTOs directly from the Lead entities — resolving cross-aggregate data (responsible/actor names from
 * Identity, latest interactions, indicator counts). It lives outside {@code application.api} so it may
 * use repositories; the visibility predicate is applied at the query level, so reads never expose a
 * Lead the caller is not allowed to see. Writes stay in the domain {@code LeadService}.
 */
@Service
@RequiredArgsConstructor
public class LeadReadService {

    private final LeadRepository leads;
    private final UserRepository users;
    private final LeadIndicatorQueries indicatorQueries;
    private final LeadAccessPolicy accessPolicy;

    /**
     * Operational, paginated Lead list filtered by the criteria and the caller's visibility.
     *
     * @param criteria the (optional) filters
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return a page of operational Lead items
     */
    @Transactional(readOnly = true)
    public Page<LeadListItemResponse> list(
            LeadSearchCriteria criteria, Pageable pageable, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Specification<Lead> spec =
                LeadSpecifications.matching(criteria).and(accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned));
        Page<Lead> page = leads.findAll(spec, pageable);

        List<UUID> leadIds = page.getContent().stream().map(Lead::id).toList();
        Map<UUID, LatestInteractionRow> latestByLead = leadIds.isEmpty()
                ? Map.of()
                : leads.findLatestInteractions(leadIds).stream()
                        .collect(Collectors.toMap(LatestInteractionRow::getLeadId, row -> row, (a, b) -> a));
        Map<UUID, String> names = resolveNames(page.getContent().stream().map(Lead::responsiblePersonId));

        return page.map(lead -> LeadListItemResponse.from(
                lead, nameOf(names, lead.responsiblePersonId()), latestByLead.get(lead.id())));
    }

    /**
     * Operational pending-items worklist visible to the caller, each with its reasons.
     *
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return a page of pending Leads
     */
    @Transactional(readOnly = true)
    public Page<PendingLeadResponse> pending(
            Pageable pageable, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Instant now = Instant.now();
        Specification<Lead> spec =
                LeadPendingSpecifications.pending(now).and(accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned));
        Page<Lead> page = leads.findAll(spec, pageable);

        List<UUID> leadIds = page.getContent().stream().map(Lead::id).toList();
        Set<UUID> withInteractions = leadIds.isEmpty()
                ? Set.of()
                : leads.findLatestInteractions(leadIds).stream()
                        .map(LatestInteractionRow::getLeadId)
                        .collect(Collectors.toSet());
        Map<UUID, String> names = resolveNames(page.getContent().stream().map(Lead::responsiblePersonId));

        return page.map(lead -> PendingLeadResponse.from(
                lead,
                nameOf(names, lead.responsiblePersonId()),
                PendingLeadReasons.of(lead, now, withInteractions.contains(lead.id()))));
    }

    /**
     * Minimum top-of-funnel indicators over the Leads visible to the caller, in an optional period.
     *
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @param from inclusive lower bound on creation (or {@code null})
     * @param to exclusive upper bound on creation (or {@code null})
     * @return the indicators
     */
    @Transactional(readOnly = true)
    public LeadIndicatorsResponse indicators(
            UUID userId, boolean canSeeAll, boolean canSeeUnassigned, Instant from, Instant to) {
        Specification<Lead> visible = accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned);

        Map<LeadStatus, Long> byStatus = indicatorQueries.countByStatus(visible, from, to);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Long> byOrigin = indicatorQueries.countByOrigin(visible, from, to);
        Map<UUID, Long> byResponsibleId = indicatorQueries.countByResponsible(visible, from, to);
        Map<UUID, String> names = resolveNames(byResponsibleId.keySet().stream());

        List<LeadIndicatorsResponse.OriginCount> originCounts = byOrigin.entrySet().stream()
                .map(e -> new LeadIndicatorsResponse.OriginCount(e.getKey(), e.getValue()))
                .toList();
        List<LeadIndicatorsResponse.ResponsibleCount> responsibleCounts = byResponsibleId.entrySet().stream()
                .map(e -> new LeadIndicatorsResponse.ResponsibleCount(
                        e.getKey() == null ? null : names.get(e.getKey()), e.getValue()))
                .toList();

        return new LeadIndicatorsResponse(
                total,
                byStatus.getOrDefault(LeadStatus.NEW, 0L),
                byStatus.getOrDefault(LeadStatus.CONTACTED, 0L),
                byStatus.getOrDefault(LeadStatus.QUALIFIED, 0L),
                byStatus.getOrDefault(LeadStatus.LOST, 0L),
                indicatorQueries.countWaitingFirstContact(visible, from, to),
                originCounts,
                responsibleCounts);
    }

    /**
     * Full detail of a Lead the caller is allowed to see (its commercial history included).
     *
     * @param id the lead id
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Lead
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return the lead detail
     * @throws LeadNotFoundException if no lead has the given id
     * @throws LeadAccessDeniedException if the lead exists but is not visible to the caller
     */
    @Transactional(readOnly = true)
    public LeadDetailResponse detail(UUID id, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        Lead lead = leads.findById(id).orElseThrow(LeadNotFoundException::new);
        if (!accessPolicy.canSee(lead, userId, canSeeAll, canSeeUnassigned)) {
            throw new LeadAccessDeniedException();
        }
        return LeadDetailResponse.from(lead, namesForDetail(lead));
    }

    /**
     * Active users that can be assigned as a Lead responsible (assignment selector + responsible filter).
     *
     * @return active users as lightweight items, ordered by username
     */
    @Transactional(readOnly = true)
    public List<ResponsibleResponse> responsibles() {
        return users.findByActiveTrueOrderByUsernameAsc().stream()
                .map(u -> new ResponsibleResponse(u.id(), u.username()))
                .toList();
    }

    private Map<UUID, String> resolveNames(Stream<UUID> ids) {
        Set<UUID> set = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty()
                ? Map.of()
                : users.findAllById(set).stream().collect(Collectors.toMap(User::id, User::username));
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    private Map<UUID, String> namesForDetail(Lead lead) {
        Stream.Builder<UUID> ids = Stream.builder();
        ids.add(lead.responsiblePersonId());
        ids.add(lead.qualifiedBy());
        ids.add(lead.lostBy());
        lead.interactions().forEach(i -> ids.add(i.registeredBy()));
        lead.assignments().forEach(a -> {
            ids.add(a.assignedBy());
            ids.add(a.fromResponsibleId());
            ids.add(a.toResponsibleId());
        });
        return resolveNames(ids.build());
    }
}
