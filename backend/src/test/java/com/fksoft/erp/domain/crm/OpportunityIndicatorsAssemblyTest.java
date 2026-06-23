package com.fksoft.erp.domain.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.repository.LeadRepository;
import com.fksoft.erp.domain.crm.repository.OpportunityIndicatorQueries;
import com.fksoft.erp.domain.crm.repository.OpportunityRepository;
import com.fksoft.erp.domain.crm.service.LeadAccessPolicy;
import com.fksoft.erp.domain.crm.service.OpportunityAccessPolicy;
import com.fksoft.erp.domain.crm.service.OpportunityService;
import com.fksoft.erp.domain.crm.service.data.OpportunityIndicators;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
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
 * Unit coverage of how {@link OpportunityService#indicators} assembles the read model from the aggregate
 * queries: the volume figures (total/lost/by stage) come from the period query, the pipeline figures
 * (active/ready-for-proposal) are derived from the snapshot query, responsible names are resolved (the
 * {@code null} id staying the unassigned bucket), and the active value/overdue figures pass through.
 */
@ExtendWith(MockitoExtension.class)
class OpportunityIndicatorsAssemblyTest {

    @Mock
    private OpportunityRepository opportunities;

    @Mock
    private LeadRepository leads;

    @Mock
    private UserRepository users;

    @Mock
    private LeadAccessPolicy leadAccessPolicy;

    @Mock
    private OpportunityAccessPolicy accessPolicy;

    @Mock
    private OpportunityIndicatorQueries indicatorQueries;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private OpportunityService service;

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");
    private static final UUID ALICE = UUID.randomUUID();

    @Test
    void assemblesVolumeFromThePeriodAndPipelineFromTheSnapshot() {
        Specification<Opportunity> visible = (root, query, cb) -> null;
        when(accessPolicy.visibleTo(any(), anyBoolean(), anyBoolean())).thenReturn(visible);

        // Volume — the period query.
        Map<String, Long> period = new LinkedHashMap<>();
        period.put("NEW_OPPORTUNITY", 3L);
        period.put("DISCOVERY", 2L);
        period.put("LOST", 1L);
        when(indicatorQueries.countByStage(any(), eq(FROM), eq(TO))).thenReturn(period);

        // Pipeline — the snapshot query (no period).
        Map<String, Long> snapshot = new LinkedHashMap<>();
        snapshot.put("NEW_OPPORTUNITY", 4L);
        snapshot.put("READY_FOR_PROPOSAL", 2L);
        snapshot.put("LOST", 3L);
        when(indicatorQueries.countByStage(any(), isNull(), isNull())).thenReturn(snapshot);

        Map<String, Long> byOrigin = new LinkedHashMap<>();
        byOrigin.put("Website", 4L);
        byOrigin.put("Indicação", 2L);
        when(indicatorQueries.countByOrigin(any(), eq(FROM), eq(TO))).thenReturn(byOrigin);

        Map<UUID, Long> byResponsible = new LinkedHashMap<>();
        byResponsible.put(ALICE, 3L);
        byResponsible.put(null, 2L); // unassigned bucket
        when(indicatorQueries.countByResponsible(any(), eq(FROM), eq(TO))).thenReturn(byResponsible);

        when(indicatorQueries.sumActivePipelineValue(any())).thenReturn(new BigDecimal("9000.00"));

        Map<UUID, BigDecimal> valueByResponsible = new LinkedHashMap<>();
        valueByResponsible.put(ALICE, new BigDecimal("6000.00"));
        valueByResponsible.put(null, new BigDecimal("3000.00"));
        when(indicatorQueries.sumActiveValueByResponsible(any())).thenReturn(valueByResponsible);

        when(indicatorQueries.countActiveOverdueClose(any(), any())).thenReturn(5L);

        User alice = mock(User.class);
        when(alice.id()).thenReturn(ALICE);
        when(alice.username()).thenReturn("Alice");
        when(users.findAllById(any())).thenReturn(List.of(alice));

        OpportunityIndicators result = service.indicators(UUID.randomUUID(), true, false, FROM, TO);

        // Volume (period).
        assertThat(result.total()).isEqualTo(6);
        assertThat(result.lost()).isEqualTo(1);
        assertThat(result.byStage())
                .containsExactly(
                        new OpportunityIndicators.StageCount("NEW_OPPORTUNITY", 3),
                        new OpportunityIndicators.StageCount("DISCOVERY", 2),
                        new OpportunityIndicators.StageCount("LOST", 1));
        assertThat(result.byOrigin())
                .containsExactly(
                        new OpportunityIndicators.OriginCount("Website", 4),
                        new OpportunityIndicators.OriginCount("Indicação", 2));
        assertThat(result.byResponsible())
                .containsExactly(
                        new OpportunityIndicators.ResponsibleCount("Alice", 3),
                        new OpportunityIndicators.ResponsibleCount(null, 2));

        // Pipeline (snapshot).
        assertThat(result.active()).isEqualTo(6); // 4 + 2 non-LOST
        assertThat(result.readyForProposal()).isEqualTo(2);
        assertThat(result.overdueClose()).isEqualTo(5);
        assertThat(result.activePipelineValue()).isEqualByComparingTo("9000.00");
        assertThat(result.valueByResponsible())
                .containsExactly(
                        new OpportunityIndicators.ResponsibleValue("Alice", new BigDecimal("6000.00")),
                        new OpportunityIndicators.ResponsibleValue(null, new BigDecimal("3000.00")));
    }
}
