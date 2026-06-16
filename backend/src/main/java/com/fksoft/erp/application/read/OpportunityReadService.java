package com.fksoft.erp.application.read;

import com.fksoft.erp.application.api.dto.OpportunityListItemResponse;
import com.fksoft.erp.domain.crm.Opportunity;
import com.fksoft.erp.domain.crm.OpportunityAccessPolicy;
import com.fksoft.erp.domain.crm.OpportunityRepository;
import com.fksoft.erp.domain.crm.OpportunitySearchCriteria;
import com.fksoft.erp.domain.crm.OpportunitySpecifications;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the Opportunity area (queries only): assembles the operational list response from the
 * Opportunity entities, resolving the responsible's name from Identity. Visibility is applied at the
 * query level. Lives outside {@code application.api} so it may use repositories. Writes stay in the
 * domain {@code OpportunityService}.
 */
@Service
@RequiredArgsConstructor
public class OpportunityReadService {

    private final OpportunityRepository opportunities;
    private final UserRepository users;
    private final OpportunityAccessPolicy accessPolicy;

    /**
     * Operational, paginated Opportunity list filtered by the criteria and the caller's visibility.
     * Lost Opportunities are excluded unless the criteria explicitly include LOST.
     *
     * @param criteria the (optional) filters
     * @param pageable page, size and sort
     * @param userId the calling user
     * @param canSeeAll whether the caller may see every Opportunity
     * @param canSeeUnassigned whether the caller may also see the unassigned pool
     * @return a page of operational Opportunity items
     */
    @Transactional(readOnly = true)
    public Page<OpportunityListItemResponse> list(
            OpportunitySearchCriteria criteria,
            Pageable pageable,
            UUID userId,
            boolean canSeeAll,
            boolean canSeeUnassigned) {
        Specification<Opportunity> spec = OpportunitySpecifications.matching(criteria)
                .and(accessPolicy.visibleTo(userId, canSeeAll, canSeeUnassigned));
        Page<Opportunity> page = opportunities.findAll(spec, pageable);

        Set<UUID> responsibleIds = page.getContent().stream()
                .map(Opportunity::responsiblePersonId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = responsibleIds.isEmpty()
                ? Map.of()
                : users.findAllById(responsibleIds).stream().collect(Collectors.toMap(User::id, User::username));

        return page.map(opportunity ->
                OpportunityListItemResponse.from(opportunity, nameOf(names, opportunity.responsiblePersonId())));
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }
}
