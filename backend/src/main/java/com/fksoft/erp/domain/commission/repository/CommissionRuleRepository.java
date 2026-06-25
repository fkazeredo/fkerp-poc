package com.fksoft.erp.domain.commission.repository;

import com.fksoft.erp.domain.commission.model.CommissionRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository for the {@link CommissionRule} aggregate (Commission Management). */
public interface CommissionRuleRepository
        extends JpaRepository<CommissionRule, UUID>, JpaSpecificationExecutor<CommissionRule> {

    /** All rules, newest first (for the management list). */
    List<CommissionRule> findAllByOrderByCreatedAtDesc();

    /**
     * The active rules, newest first — the only rules usable for new commission calculation (a later slice). An
     * inactive rule is kept for history but never applied to a new commission.
     *
     * @return the active rules
     */
    List<CommissionRule> findByActiveTrueOrderByCreatedAtDesc();
}
