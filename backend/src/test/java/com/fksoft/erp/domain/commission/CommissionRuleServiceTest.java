package com.fksoft.erp.domain.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fksoft.erp.domain.commission.exception.CommissionRulePercentageAboveLimitException;
import com.fksoft.erp.domain.commission.exception.CommissionRuleTargetUserNotFoundException;
import com.fksoft.erp.domain.commission.model.CommissionRule;
import com.fksoft.erp.domain.commission.model.CommissionTargetType;
import com.fksoft.erp.domain.commission.repository.CommissionRuleRepository;
import com.fksoft.erp.domain.commission.service.CommissionRuleService;
import com.fksoft.erp.domain.commission.service.data.CommissionRuleListItem;
import com.fksoft.erp.domain.commission.service.data.CreateCommissionRuleCommand;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests of the Commission Rule Application Service with the repository and users mocked. */
@ExtendWith(MockitoExtension.class)
class CommissionRuleServiceTest {

    private static final BigDecimal SAFE_LIMIT = new BigDecimal("50");
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Mock
    private CommissionRuleRepository rules;

    @Mock
    private UserRepository users;

    @InjectMocks
    private CommissionRuleService service;

    private final UUID userId = UUID.randomUUID();

    private CreateCommissionRuleCommand command(BigDecimal percentage, UUID targetUserId, boolean allowAboveLimit) {
        return new CreateCommissionRuleCommand(
                "Padrão", percentage, CommissionTargetType.SELLER, targetUserId, START, null, "nota", allowAboveLimit);
    }

    @Test
    void createBuildsAndSavesAnActiveRule() {
        when(rules.save(any(CommissionRule.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID id = service.create(command(new BigDecimal("5"), null, false), userId, SAFE_LIMIT);

        ArgumentCaptor<CommissionRule> saved = ArgumentCaptor.forClass(CommissionRule.class);
        verify(rules).save(saved.capture());
        assertThat(saved.getValue().active()).isTrue();
        assertThat(saved.getValue().percentage()).isEqualByComparingTo("5.00");
        assertThat(saved.getValue().createdBy()).isEqualTo(userId);
        assertThat(id).isEqualTo(saved.getValue().id());
    }

    @Test
    void createAtTheSafeLimitSucceeds() {
        when(rules.save(any(CommissionRule.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(command(new BigDecimal("50"), null, false), userId, SAFE_LIMIT);
        verify(rules).save(any());
    }

    @Test
    void createAboveTheSafeLimitWithoutTheFlagIsRejected() {
        assertThatThrownBy(() -> service.create(command(new BigDecimal("60"), null, false), userId, SAFE_LIMIT))
                .isInstanceOf(CommissionRulePercentageAboveLimitException.class);
        verify(rules, never()).save(any());
    }

    @Test
    void createAboveTheSafeLimitWithTheExplicitFlagSucceeds() {
        when(rules.save(any(CommissionRule.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(command(new BigDecimal("60"), null, true), userId, SAFE_LIMIT);
        ArgumentCaptor<CommissionRule> saved = ArgumentCaptor.forClass(CommissionRule.class);
        verify(rules).save(saved.capture());
        assertThat(saved.getValue().percentage()).isEqualByComparingTo("60.00");
    }

    @Test
    void createRejectsAnUnknownOrInactiveTargetUser() {
        UUID target = UUID.randomUUID();
        when(users.findById(target)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(new BigDecimal("5"), target, false), userId, SAFE_LIMIT))
                .isInstanceOf(CommissionRuleTargetUserNotFoundException.class);
        verify(rules, never()).save(any());
    }

    @Test
    void createWithAValidActiveTargetUserSucceeds() {
        UUID target = UUID.randomUUID();
        User user = mock(User.class);
        when(user.active()).thenReturn(true);
        when(users.findById(target)).thenReturn(Optional.of(user));
        when(rules.save(any(CommissionRule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(command(new BigDecimal("5"), target, false), userId, SAFE_LIMIT);

        ArgumentCaptor<CommissionRule> saved = ArgumentCaptor.forClass(CommissionRule.class);
        verify(rules).save(saved.capture());
        assertThat(saved.getValue().targetUserId()).isEqualTo(target);
    }

    @Test
    void listActiveOnlyVersusIncludeInactive() {
        CommissionRule active = CommissionRule.create(
                new com.fksoft.erp.domain.commission.model.CommissionRuleData(
                        "A", new BigDecimal("5"), CommissionTargetType.SELLER, null, START, null, null),
                userId);
        when(rules.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of(active));
        when(rules.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(active));

        List<CommissionRuleListItem> activeOnly = service.list(false);
        assertThat(activeOnly).hasSize(1);
        assertThat(activeOnly.get(0).active()).isTrue();
        verify(rules).findByActiveTrueOrderByCreatedAtDesc();

        service.list(true);
        verify(rules).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void activateAndDeactivateReturnTheRefreshedDetail() {
        CommissionRule rule = CommissionRule.create(
                new com.fksoft.erp.domain.commission.model.CommissionRuleData(
                        "A", new BigDecimal("5"), CommissionTargetType.SELLER, null, START, null, null),
                userId);
        when(rules.findById(rule.id())).thenReturn(Optional.of(rule));

        assertThat(service.deactivate(rule.id(), userId).active()).isFalse();
        assertThat(service.activate(rule.id(), userId).active()).isTrue();
    }

    @Test
    void creatingARuleTouchesOnlyTheRuleRepository() {
        // The service has no Commission/Payment repository at all — creating a rule cannot create those. With no
        // target user set, it does not even read the users repository (no cross-context access).
        when(rules.save(any(CommissionRule.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(command(new BigDecimal("5"), null, false), userId, SAFE_LIMIT);
        verify(users, never()).findById(any());
    }
}
