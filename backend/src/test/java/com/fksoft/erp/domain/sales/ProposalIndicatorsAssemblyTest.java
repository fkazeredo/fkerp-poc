package com.fksoft.erp.domain.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.service.OpportunityAccessPolicy;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.ProposalIndicatorQueries;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.domain.sales.service.ProposalAccessPolicy;
import com.fksoft.erp.domain.sales.service.ProposalService;
import com.fksoft.erp.domain.sales.service.data.ProposalIndicators;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit coverage of how {@link ProposalService#indicators} assembles the read model from the aggregate
 * queries: the volume figures (total / by status / by responsible / proposed amount / accepted amount /
 * rejected count) come from the period queries, the operational figures (waiting for review / customer
 * decision) are derived from the snapshot query, and responsible names are resolved (the {@code null} id
 * staying the unassigned bucket).
 */
@ExtendWith(MockitoExtension.class)
class ProposalIndicatorsAssemblyTest {

    @Mock
    private ProposalRepository proposals;

    @Mock
    private ProposalAccessPolicy accessPolicy;

    @Mock
    private ProposalIndicatorQueries indicatorQueries;

    @Mock
    private OpportunityRepository opportunities;

    @Mock
    private OpportunityAccessPolicy opportunityAccessPolicy;

    @Mock
    private LeadRepository leads;

    @Mock
    private UserRepository users;

    @Mock
    private CommercialOrderRepository orders;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private ProposalService service;

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");
    private static final UUID ALICE = UUID.randomUUID();

    @Test
    void assemblesVolumeFromThePeriodAndOperationalFromTheSnapshot() {
        Specification<Proposal> visible = (root, query, cb) -> null;
        when(accessPolicy.visibleTo(any(), anyBoolean(), anyBoolean())).thenReturn(visible);

        // Volume — the period queries.
        Map<String, Long> periodCounts = new LinkedHashMap<>();
        periodCounts.put("DRAFT", 3L);
        periodCounts.put("ACCEPTED", 2L);
        periodCounts.put("REJECTED", 1L);
        when(indicatorQueries.countByStatus(any(), eq(FROM), eq(TO))).thenReturn(periodCounts);

        Map<String, BigDecimal> periodSums = new LinkedHashMap<>();
        periodSums.put("DRAFT", new BigDecimal("1000.00"));
        periodSums.put("ACCEPTED", new BigDecimal("5000.00"));
        periodSums.put("REJECTED", new BigDecimal("800.00"));
        when(indicatorQueries.sumTotalByStatus(any(), eq(FROM), eq(TO))).thenReturn(periodSums);

        Map<UUID, Long> byResponsible = new LinkedHashMap<>();
        byResponsible.put(ALICE, 4L);
        byResponsible.put(null, 2L); // unassigned bucket
        when(indicatorQueries.countByResponsible(any(), eq(FROM), eq(TO))).thenReturn(byResponsible);

        // Operational — the snapshot query (no period).
        Map<String, Long> snapshot = new LinkedHashMap<>();
        snapshot.put("READY_FOR_REVIEW", 2L);
        snapshot.put("SENT", 5L);
        snapshot.put("DRAFT", 9L);
        when(indicatorQueries.countByStatus(any(), isNull(), isNull())).thenReturn(snapshot);

        User alice = mock(User.class);
        when(alice.id()).thenReturn(ALICE);
        when(alice.username()).thenReturn("Alice");
        when(users.findAllById(any())).thenReturn(List.of(alice));

        ProposalIndicators result = service.indicators(UUID.randomUUID(), true, false, FROM, TO);

        // Volume (period).
        assertThat(result.total()).isEqualTo(6); // 3 + 2 + 1
        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(result.proposedAmount()).isEqualByComparingTo("6800.00"); // 1000 + 5000 + 800
        assertThat(result.acceptedAmount()).isEqualByComparingTo("5000.00");
        assertThat(result.byStatus())
                .containsExactlyInAnyOrder(
                        new ProposalIndicators.StatusCount("DRAFT", 3),
                        new ProposalIndicators.StatusCount("ACCEPTED", 2),
                        new ProposalIndicators.StatusCount("REJECTED", 1));
        assertThat(result.byResponsible())
                .containsExactly(
                        new ProposalIndicators.ResponsibleCount("Alice", 4),
                        new ProposalIndicators.ResponsibleCount(null, 2));

        // Operational (snapshot — ignores the period).
        assertThat(result.waitingForReview()).isEqualTo(2);
        assertThat(result.waitingForCustomerDecision()).isEqualTo(5);
    }
}
