package com.fksoft.erp.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fksoft.erp.application.api.dto.LeadDetailResponse;
import com.fksoft.erp.application.api.dto.LeadIndicatorsResponse;
import com.fksoft.erp.application.api.dto.LeadListItemResponse;
import com.fksoft.erp.domain.crm.LatestInteractionRow;
import com.fksoft.erp.domain.crm.Lead;
import com.fksoft.erp.domain.crm.LeadAccessDeniedException;
import com.fksoft.erp.domain.crm.LeadAccessPolicy;
import com.fksoft.erp.domain.crm.LeadIndicatorQueries;
import com.fksoft.erp.domain.crm.LeadNotFoundException;
import com.fksoft.erp.domain.crm.LeadRepository;
import com.fksoft.erp.domain.crm.LeadSearchCriteria;
import com.fksoft.erp.domain.crm.LeadStatus;
import com.fksoft.erp.domain.crm.Origin;
import com.fksoft.erp.domain.crm.RegisterLeadCommand;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests of the Lead read side: it assembles the {@code *Response} DTOs from the entities, resolves
 * responsible names from Identity, merges the latest-interaction rows, and derives the indicators —
 * applying the visibility predicate at the query level.
 */
@ExtendWith(MockitoExtension.class)
class LeadReadServiceTest {

    @Mock
    private LeadRepository leads;

    @Mock
    private UserRepository users;

    @Mock
    private LeadIndicatorQueries indicatorQueries;

    @Mock
    private LeadAccessPolicy accessPolicy;

    @InjectMocks
    private LeadReadService service;

    @Test
    void listBuildsItemsWithMainContactResponsibleAndLatestInteraction() {
        UUID responsibleA = UUID.randomUUID();
        Origin origin = Origin.create("WEBSITE", "Website", 1);
        Lead withPhone = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), responsibleA, null),
                origin,
                UUID.randomUUID());
        Lead unassignedEmail = Lead.register(
                new RegisterLeadCommand("Joao", null, null, "joao@example.com", UUID.randomUUID(), null, null),
                origin,
                UUID.randomUUID());
        Pageable pageable = PageRequest.of(0, 20);

        when(accessPolicy.visibleTo(any(), anyBoolean(), anyBoolean())).thenReturn((root, query, cb) -> null);
        when(leads.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(withPhone, unassignedEmail), pageable, 2));
        LatestInteractionRow row = mock(LatestInteractionRow.class);
        when(row.getLeadId()).thenReturn(withPhone.id());
        when(row.getOccurredAt()).thenReturn(Instant.parse("2026-06-15T10:00:00Z"));
        when(row.getTypeLabel()).thenReturn("Ligação");
        when(leads.findLatestInteractions(anyList())).thenReturn(List.of(row));
        User responsible = mock(User.class);
        when(responsible.id()).thenReturn(responsibleA);
        when(responsible.username()).thenReturn("ana");
        when(users.findAllById(any())).thenReturn(List.of(responsible));

        Page<LeadListItemResponse> page = service.list(
                new LeadSearchCriteria(null, null, null, false, null, null, null),
                pageable,
                UUID.randomUUID(),
                false,
                true);

        LeadListItemResponse assigned = page.getContent().get(0);
        assertThat(assigned.name()).isEqualTo("Maria");
        assertThat(assigned.mainContact()).isEqualTo("11999999999");
        assertThat(assigned.responsibleName()).isEqualTo("ana");
        assertThat(assigned.unassigned()).isFalse();
        assertThat(assigned.lastInteractionType()).isEqualTo("Ligação");
        assertThat(assigned.lastInteractionAt()).isEqualTo(Instant.parse("2026-06-15T10:00:00Z"));

        LeadListItemResponse unassigned = page.getContent().get(1);
        assertThat(unassigned.mainContact()).isEqualTo("joao@example.com");
        assertThat(unassigned.responsibleName()).isNull();
        assertThat(unassigned.unassigned()).isTrue();
        assertThat(unassigned.lastInteractionAt()).isNull();
    }

    @Test
    void detailReturnsResponseForAVisibleLead() {
        UUID id = UUID.randomUUID();
        Lead lead = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), null, null),
                Origin.create("WEBSITE", "Website", 1),
                UUID.randomUUID());
        when(leads.findById(id)).thenReturn(Optional.of(lead));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(true);

        LeadDetailResponse response = service.detail(id, UUID.randomUUID(), false, false);

        assertThat(response.name()).isEqualTo("Maria");
        assertThat(response.status()).isEqualTo(LeadStatus.NEW);
        assertThat(response.interactions()).isEmpty();
        assertThat(response.qualification()).isNull();
        assertThat(response.loss()).isNull();
    }

    @Test
    void detailThrowsNotFoundWhenAbsent() {
        UUID id = UUID.randomUUID();
        when(leads.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(id, UUID.randomUUID(), false, false))
                .isInstanceOf(LeadNotFoundException.class);
    }

    @Test
    void detailThrowsAccessDeniedWhenNotVisible() {
        UUID id = UUID.randomUUID();
        Lead lead = Lead.register(
                new RegisterLeadCommand("Maria", "11999999999", null, null, UUID.randomUUID(), UUID.randomUUID(), null),
                Origin.create("WEBSITE", "Website", 1),
                UUID.randomUUID());
        when(leads.findById(id)).thenReturn(Optional.of(lead));
        when(accessPolicy.canSee(any(Lead.class), any(), anyBoolean(), anyBoolean()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.detail(id, UUID.randomUUID(), false, false))
                .isInstanceOf(LeadAccessDeniedException.class);
    }

    @Test
    void indicatorsDeriveTotalFromStatusesAndResolveResponsibleNames() {
        UUID r1 = UUID.randomUUID();
        when(accessPolicy.visibleTo(any(), anyBoolean(), anyBoolean())).thenReturn((root, q, cb) -> null);
        when(indicatorQueries.countByStatus(any(), any(), any()))
                .thenReturn(Map.of(LeadStatus.NEW, 3L, LeadStatus.LOST, 2L));
        when(indicatorQueries.countByOrigin(any(), any(), any())).thenReturn(Map.of("Website", 4L));
        LinkedHashMap<UUID, Long> byResponsible = new LinkedHashMap<>();
        byResponsible.put(r1, 3L);
        byResponsible.put(null, 2L);
        when(indicatorQueries.countByResponsible(any(), any(), any())).thenReturn(byResponsible);
        when(indicatorQueries.countWaitingFirstContact(any(), any(), any())).thenReturn(1L);
        User user = mock(User.class);
        when(user.id()).thenReturn(r1);
        when(user.username()).thenReturn("ana");
        when(users.findAllById(any())).thenReturn(List.of(user));

        LeadIndicatorsResponse view = service.indicators(UUID.randomUUID(), false, false, null, null);

        assertThat(view.total()).isEqualTo(5);
        assertThat(view.newLeads()).isEqualTo(3);
        assertThat(view.lost()).isEqualTo(2);
        assertThat(view.contacted()).isZero();
        assertThat(view.qualified()).isZero();
        assertThat(view.waitingFirstContact()).isEqualTo(1);
        assertThat(view.byOrigin()).containsExactly(new LeadIndicatorsResponse.OriginCount("Website", 4L));
        assertThat(view.byResponsible())
                .containsExactlyInAnyOrder(
                        new LeadIndicatorsResponse.ResponsibleCount("ana", 3L),
                        new LeadIndicatorsResponse.ResponsibleCount(null, 2L));
    }
}
