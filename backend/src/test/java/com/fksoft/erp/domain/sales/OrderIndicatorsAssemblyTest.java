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
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import com.fksoft.erp.domain.sales.repository.CommercialOrderRepository;
import com.fksoft.erp.domain.sales.repository.OrderIndicatorQueries;
import com.fksoft.erp.domain.sales.repository.ProposalRepository;
import com.fksoft.erp.domain.sales.service.CommercialOrderService;
import com.fksoft.erp.domain.sales.service.OrderAccessPolicy;
import com.fksoft.erp.domain.sales.service.ProposalAccessPolicy;
import com.fksoft.erp.domain.sales.service.data.OrderIndicators;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
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
 * Unit coverage of how {@link CommercialOrderService#indicators} assembles the read model from the
 * aggregate queries: the volume figures (total / total amount / by responsible) come from the period
 * queries, the operational figure (pending booking) is derived from the snapshot query, and responsible
 * names are resolved (the {@code null} id staying the unassigned bucket).
 */
@ExtendWith(MockitoExtension.class)
class OrderIndicatorsAssemblyTest {

    @Mock
    private CommercialOrderRepository orders;

    @Mock
    private OrderAccessPolicy accessPolicy;

    @Mock
    private OrderIndicatorQueries indicatorQueries;

    @Mock
    private ProposalRepository proposals;

    @Mock
    private ProposalAccessPolicy proposalAccessPolicy;

    @Mock
    private OpportunityRepository opportunities;

    @Mock
    private LeadRepository leads;

    @Mock
    private UserRepository users;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private CommercialOrderService service;

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");
    private static final UUID ALICE = UUID.randomUUID();

    @Test
    void assemblesVolumeFromThePeriodAndPendingBookingFromTheSnapshot() {
        Specification<CommercialOrder> visible = (root, query, cb) -> null;
        when(accessPolicy.visibleTo(any(), anyBoolean(), anyBoolean())).thenReturn(visible);

        // Volume — the period queries.
        Map<CommercialOrderStatus, Long> periodCounts = new EnumMap<>(CommercialOrderStatus.class);
        periodCounts.put(CommercialOrderStatus.PENDING_BOOKING, 3L);
        periodCounts.put(CommercialOrderStatus.BOOKING_NOT_REQUIRED, 2L);
        when(indicatorQueries.countByStatus(any(), eq(FROM), eq(TO))).thenReturn(periodCounts);

        when(indicatorQueries.sumTotal(any(), eq(FROM), eq(TO))).thenReturn(new BigDecimal("12000.00"));

        Map<UUID, Long> byResponsible = new LinkedHashMap<>();
        byResponsible.put(ALICE, 4L);
        byResponsible.put(null, 1L); // unassigned bucket
        when(indicatorQueries.countByResponsible(any(), eq(FROM), eq(TO))).thenReturn(byResponsible);

        // Operational — the snapshot query (no period).
        Map<CommercialOrderStatus, Long> snapshot = new EnumMap<>(CommercialOrderStatus.class);
        snapshot.put(CommercialOrderStatus.PENDING_BOOKING, 7L);
        snapshot.put(CommercialOrderStatus.BOOKING_NOT_REQUIRED, 3L);
        when(indicatorQueries.countByStatus(any(), isNull(), isNull())).thenReturn(snapshot);

        User alice = mock(User.class);
        when(alice.id()).thenReturn(ALICE);
        when(alice.username()).thenReturn("Alice");
        when(users.findAllById(any())).thenReturn(List.of(alice));

        OrderIndicators result = service.indicators(UUID.randomUUID(), true, false, FROM, TO);

        // Volume (period).
        assertThat(result.total()).isEqualTo(5); // 3 + 2
        assertThat(result.totalAmount()).isEqualByComparingTo("12000.00");
        assertThat(result.byResponsible())
                .containsExactly(
                        new OrderIndicators.ResponsibleCount("Alice", 4),
                        new OrderIndicators.ResponsibleCount(null, 1));

        // Operational (snapshot — ignores the period).
        assertThat(result.pendingBooking()).isEqualTo(7);
    }
}
