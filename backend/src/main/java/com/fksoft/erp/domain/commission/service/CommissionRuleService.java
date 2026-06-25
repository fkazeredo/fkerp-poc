package com.fksoft.erp.domain.commission.service;

import com.fksoft.erp.domain.commission.exception.CommissionRuleNotFoundException;
import com.fksoft.erp.domain.commission.exception.CommissionRulePercentageAboveLimitException;
import com.fksoft.erp.domain.commission.exception.CommissionRuleTargetUserNotFoundException;
import com.fksoft.erp.domain.commission.model.CommissionRule;
import com.fksoft.erp.domain.commission.model.CommissionRuleData;
import com.fksoft.erp.domain.commission.repository.CommissionRuleRepository;
import com.fksoft.erp.domain.commission.service.data.CommissionRuleDetail;
import com.fksoft.erp.domain.commission.service.data.CommissionRuleListItem;
import com.fksoft.erp.domain.commission.service.data.CreateCommissionRuleCommand;
import com.fksoft.erp.domain.identity.User;
import com.fksoft.erp.domain.identity.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service of Commission Management for the Commission <b>Rule</b> (configuration). It creates, lists,
 * reads, updates and activates/deactivates rules — and nothing else: it <b>never</b> creates a Commission record,
 * a Payment, payroll, payable, tax or accounting data, and it never takes ownership of the Commercial Order or the
 * Receivable. The soft safe-percentage limit is supplied by the caller (the delivery layer reads it from
 * configuration), keeping the domain free of infrastructure concerns.
 */
@Service
@RequiredArgsConstructor
public class CommissionRuleService {

    private final CommissionRuleRepository rules;
    private final UserRepository users;

    /**
     * Creates a Commission Rule. Validates the target user (when a specific one is set) and the soft safe-percentage
     * limit (unless the caller explicitly allows exceeding it); the hard invariants (percentage in {@code (0, 100]},
     * dates) are enforced by the entity. Creates no Commission/Payment data.
     *
     * @param cmd the rule data
     * @param userId the authenticated manager creating the rule
     * @param safeMaxPercentage the configured safe limit for the percentage
     * @return the new rule id
     * @throws CommissionRuleTargetUserNotFoundException if a target user is set but missing/inactive
     * @throws CommissionRulePercentageAboveLimitException if the percentage exceeds the safe limit without consent
     */
    @Transactional
    public UUID create(CreateCommissionRuleCommand cmd, UUID userId, BigDecimal safeMaxPercentage) {
        validateTargetUser(cmd.targetUserId());
        requireWithinSafeLimit(cmd, safeMaxPercentage);
        CommissionRule rule = CommissionRule.create(toData(cmd), userId);
        rules.save(rule);
        return rule.id();
    }

    /**
     * Lists the Commission Rules (newest first) for the management screen.
     *
     * @param includeInactive whether to include inactive rules
     * @return the rule list items
     */
    @Transactional(readOnly = true)
    public List<CommissionRuleListItem> list(boolean includeInactive) {
        List<CommissionRule> found =
                includeInactive ? rules.findAllByOrderByCreatedAtDesc() : rules.findByActiveTrueOrderByCreatedAtDesc();
        Map<UUID, String> names = resolveNames(found.stream().map(CommissionRule::targetUserId));
        return found.stream()
                .map(rule -> CommissionRuleListItem.from(rule, nameOf(names, rule.targetUserId())))
                .toList();
    }

    /**
     * Full detail of a Commission Rule.
     *
     * @param id the rule id
     * @return the detail read model
     * @throws CommissionRuleNotFoundException if the rule does not exist
     */
    @Transactional(readOnly = true)
    public CommissionRuleDetail detail(UUID id) {
        return toDetail(rules.findById(id).orElseThrow(CommissionRuleNotFoundException::new));
    }

    /**
     * Updates a Commission Rule's editable fields (same validations as creation). Does not change the active flag.
     *
     * @param id the rule id
     * @param cmd the new data
     * @param userId the authenticated manager
     * @param safeMaxPercentage the configured safe limit for the percentage
     * @return the refreshed detail
     * @throws CommissionRuleNotFoundException if the rule does not exist
     */
    @Transactional
    public CommissionRuleDetail update(
            UUID id, CreateCommissionRuleCommand cmd, UUID userId, BigDecimal safeMaxPercentage) {
        CommissionRule rule = rules.findById(id).orElseThrow(CommissionRuleNotFoundException::new);
        validateTargetUser(cmd.targetUserId());
        requireWithinSafeLimit(cmd, safeMaxPercentage);
        rule.update(toData(cmd), userId);
        return toDetail(rule);
    }

    /**
     * Activates a rule (it becomes usable for new commission calculation).
     *
     * @param id the rule id
     * @param userId the authenticated manager
     * @return the refreshed detail
     */
    @Transactional
    public CommissionRuleDetail activate(UUID id, UUID userId) {
        CommissionRule rule = rules.findById(id).orElseThrow(CommissionRuleNotFoundException::new);
        rule.activate(userId);
        return toDetail(rule);
    }

    /**
     * Deactivates a rule (kept for history; not used for new calculation).
     *
     * @param id the rule id
     * @param userId the authenticated manager
     * @return the refreshed detail
     */
    @Transactional
    public CommissionRuleDetail deactivate(UUID id, UUID userId) {
        CommissionRule rule = rules.findById(id).orElseThrow(CommissionRuleNotFoundException::new);
        rule.deactivate(userId);
        return toDetail(rule);
    }

    private static CommissionRuleData toData(CreateCommissionRuleCommand cmd) {
        return new CommissionRuleData(
                cmd.name(),
                cmd.percentage(),
                cmd.targetType(),
                cmd.targetUserId(),
                cmd.startDate(),
                cmd.endDate(),
                cmd.notes());
    }

    private void validateTargetUser(UUID targetUserId) {
        if (targetUserId != null
                && users.findById(targetUserId).filter(User::active).isEmpty()) {
            throw new CommissionRuleTargetUserNotFoundException();
        }
    }

    private void requireWithinSafeLimit(CreateCommissionRuleCommand cmd, BigDecimal safeMaxPercentage) {
        if (!cmd.allowAboveLimit()
                && cmd.percentage() != null
                && cmd.percentage().compareTo(safeMaxPercentage) > 0) {
            throw new CommissionRulePercentageAboveLimitException(safeMaxPercentage);
        }
    }

    private CommissionRuleDetail toDetail(CommissionRule rule) {
        String name = rule.targetUserId() == null
                ? null
                : users.findById(rule.targetUserId()).map(User::username).orElse(null);
        return CommissionRuleDetail.from(rule, name);
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
}
