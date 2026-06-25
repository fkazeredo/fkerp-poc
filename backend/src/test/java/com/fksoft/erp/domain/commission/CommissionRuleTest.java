package com.fksoft.erp.domain.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.erp.domain.commission.exception.CommissionRuleDatesInvalidException;
import com.fksoft.erp.domain.commission.exception.CommissionRulePercentageInvalidException;
import com.fksoft.erp.domain.commission.model.CommissionRule;
import com.fksoft.erp.domain.commission.model.CommissionRuleData;
import com.fksoft.erp.domain.commission.model.CommissionTargetType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link CommissionRule} aggregate: the hard invariants on creation/update and the toggles. */
class CommissionRuleTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private final UUID by = UUID.randomUUID();

    private static CommissionRuleData data(
            String name, BigDecimal percentage, LocalDate start, LocalDate end, String notes) {
        return new CommissionRuleData(name, percentage, CommissionTargetType.SELLER, null, start, end, notes);
    }

    private CommissionRule rule(BigDecimal percentage, LocalDate start, LocalDate end) {
        return CommissionRule.create(data("Padrão vendedores", percentage, start, end, "obs"), by);
    }

    @Test
    void createBuildsAnActiveRuleNormalizingScaleAndTrimming() {
        CommissionRule rule = CommissionRule.create(data("  Padrão  ", new BigDecimal("5"), START, null, "nota"), by);

        assertThat(rule.id()).isNotNull();
        assertThat(rule.name()).isEqualTo("Padrão");
        assertThat(rule.percentage()).isEqualByComparingTo("5.00");
        assertThat(rule.percentage().scale()).isEqualTo(2);
        assertThat(rule.targetType()).isEqualTo(CommissionTargetType.SELLER);
        assertThat(rule.active()).isTrue();
        assertThat(rule.startDate()).isEqualTo(START);
        assertThat(rule.endDate()).isNull();
        assertThat(rule.notes()).isEqualTo("nota");
        assertThat(rule.createdBy()).isEqualTo(by);
        assertThat(rule.updatedBy()).isEqualTo(by);
    }

    @Test
    void createKeepsBlankNotesNull() {
        assertThat(CommissionRule.create(data("R", new BigDecimal("3.5"), START, null, "   "), by)
                        .notes())
                .isNull();
    }

    @Test
    void createRejectsZeroOrNegativePercentage() {
        assertThatThrownBy(() -> rule(BigDecimal.ZERO, START, null))
                .isInstanceOf(CommissionRulePercentageInvalidException.class);
        assertThatThrownBy(() -> rule(new BigDecimal("-1"), START, null))
                .isInstanceOf(CommissionRulePercentageInvalidException.class);
    }

    @Test
    void createRejectsPercentageAbove100() {
        assertThatThrownBy(() -> rule(new BigDecimal("100.01"), START, null))
                .isInstanceOf(CommissionRulePercentageInvalidException.class);
    }

    @Test
    void createAcceptsExactly100() {
        assertThat(rule(new BigDecimal("100"), START, null).percentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void createRejectsAnEndDateBeforeTheStart() {
        assertThatThrownBy(() -> rule(new BigDecimal("5"), START, START.minusDays(1)))
                .isInstanceOf(CommissionRuleDatesInvalidException.class);
    }

    @Test
    void createRejectsANullStartDate() {
        assertThatThrownBy(() -> rule(new BigDecimal("5"), null, null))
                .isInstanceOf(CommissionRuleDatesInvalidException.class);
    }

    @Test
    void activateAndDeactivateToggleTheFlag() {
        CommissionRule rule = rule(new BigDecimal("5"), START, null);
        UUID other = UUID.randomUUID();

        rule.deactivate(other);
        assertThat(rule.active()).isFalse();
        assertThat(rule.updatedBy()).isEqualTo(other);

        rule.activate(by);
        assertThat(rule.active()).isTrue();
        assertThat(rule.updatedBy()).isEqualTo(by);
    }

    @Test
    void updateChangesFieldsAndRevalidates() {
        CommissionRule rule = rule(new BigDecimal("5"), START, null);
        UUID editor = UUID.randomUUID();

        rule.update(
                new CommissionRuleData(
                        "Representantes",
                        new BigDecimal("7.25"),
                        CommissionTargetType.SALES_REPRESENTATIVE,
                        null,
                        START,
                        START.plusMonths(6),
                        "novo"),
                editor);

        assertThat(rule.name()).isEqualTo("Representantes");
        assertThat(rule.percentage()).isEqualByComparingTo("7.25");
        assertThat(rule.targetType()).isEqualTo(CommissionTargetType.SALES_REPRESENTATIVE);
        assertThat(rule.endDate()).isEqualTo(START.plusMonths(6));
        assertThat(rule.updatedBy()).isEqualTo(editor);

        assertThatThrownBy(() -> rule.update(data("x", new BigDecimal("0"), START, null, null), editor))
                .isInstanceOf(CommissionRulePercentageInvalidException.class);
    }
}
