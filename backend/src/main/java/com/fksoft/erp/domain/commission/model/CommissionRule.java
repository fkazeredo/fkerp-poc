package com.fksoft.erp.domain.commission.model;

import com.fksoft.erp.domain.commission.exception.CommissionRuleDatesInvalidException;
import com.fksoft.erp.domain.commission.exception.CommissionRulePercentageInvalidException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A Commission Rule (Commission Management): an admin-managed configuration that says how much commission a
 * commercial actor earns — for Sprint 6, a <b>percentage of the received amount</b>. It is <b>configuration
 * only</b>: it does NOT create or hold Commission records, Payments, payroll, payable, tax or accounting data;
 * Commission Management owns the rule but not the Commercial Order or the Receivable. A rule targets a kind of
 * commercial actor ({@link CommissionTargetType}) and, optionally, a specific user; it has a validity window and
 * an active flag. Only <b>active</b> rules may be used for new commission calculation (a later slice).
 */
@Entity
@Table(name = "commission_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionRule {

    private static final int SCALE = 2;
    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false)
    private String name;

    // The commission percentage of the received amount: > 0 and ≤ 100 (scale 2).
    @NotNull
    @Positive
    @Column(nullable = false)
    private BigDecimal percentage;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private CommissionTargetType targetType;

    // When set, the rule applies to this specific user; when null, to all actors of the target type.
    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(nullable = false)
    private boolean active;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Size(max = 2000)
    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    /**
     * Creates a new <b>active</b> Commission Rule, enforcing the hard invariants (the soft safe-percentage limit is
     * checked by the Application Service, which holds the configured limit + the explicit override). Creates no
     * Commission/Payment data.
     *
     * @param data the rule's defining attributes (percentage in {@code (0, 100]}, valid dates)
     * @param createdBy id of the user creating the rule
     * @return a new, unsaved, active rule
     * @throws CommissionRulePercentageInvalidException if the percentage is not in {@code (0, 100]}
     * @throws CommissionRuleDatesInvalidException if the end date is before the start date
     */
    public static CommissionRule create(CommissionRuleData data, UUID createdBy) {
        requireValidPercentage(data.percentage());
        requireValidDates(data.startDate(), data.endDate());
        CommissionRule rule = new CommissionRule();
        rule.id = UUID.randomUUID();
        rule.apply(data);
        rule.active = true;
        rule.createdBy = createdBy;
        rule.updatedBy = createdBy;
        return rule;
    }

    /**
     * Updates the rule's editable fields (the same hard invariants apply). Does not change the active flag.
     *
     * @param data the new defining attributes
     * @param updatedBy id of the user updating the rule
     * @throws CommissionRulePercentageInvalidException if the percentage is not in {@code (0, 100]}
     * @throws CommissionRuleDatesInvalidException if the end date is before the start date
     */
    public void update(CommissionRuleData data, UUID updatedBy) {
        requireValidPercentage(data.percentage());
        requireValidDates(data.startDate(), data.endDate());
        apply(data);
        this.updatedBy = updatedBy;
    }

    private void apply(CommissionRuleData data) {
        this.name = data.name() == null ? null : data.name().strip();
        this.percentage = data.percentage().setScale(SCALE, RoundingMode.HALF_UP);
        this.targetType = data.targetType();
        this.targetUserId = data.targetUserId();
        this.startDate = data.startDate();
        this.endDate = data.endDate();
        this.notes = emptyToNull(data.notes());
    }

    /** Activates the rule (it becomes usable for new commission calculation). */
    public void activate(UUID byUser) {
        this.active = true;
        this.updatedBy = byUser;
    }

    /** Deactivates the rule (it is kept for history but cannot be used for new calculation). */
    public void deactivate(UUID byUser) {
        this.active = false;
        this.updatedBy = byUser;
    }

    private static void requireValidPercentage(BigDecimal percentage) {
        if (percentage == null || percentage.signum() <= 0 || percentage.compareTo(MAX_PERCENTAGE) > 0) {
            throw new CommissionRulePercentageInvalidException();
        }
    }

    private static void requireValidDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new CommissionRuleDatesInvalidException();
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new CommissionRuleDatesInvalidException();
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
