package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.CommissionRuleResponse;
import com.fksoft.erp.application.api.dto.CreateCommissionRuleRequest;
import com.fksoft.erp.domain.commission.service.CommissionRuleService;
import com.fksoft.erp.domain.commission.service.data.CommissionRuleDetail;
import com.fksoft.erp.domain.commission.service.data.CommissionRuleListItem;
import com.fksoft.erp.domain.commission.service.data.CreateCommissionRuleCommand;
import com.fksoft.erp.infra.config.CommissionProperties;
import com.fksoft.erp.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commission Rule endpoints (Commission Management). Managing rules requires {@code commission:rule:manage}
 * (commercial/financial managers). A rule is configuration only — creating/editing it never creates a Commission
 * record, a Payment, payroll, payable, tax or accounting data. The configured safe-percentage limit is read here
 * (delivery) and passed to the domain service, keeping the domain free of infrastructure concerns.
 */
@RestController
@RequestMapping("/api/commission/rules")
@RequiredArgsConstructor
public class CommissionRuleController {

    private final CommissionRuleService ruleService;
    private final CommissionProperties commissionProperties;
    private final UserContextProvider userContext;

    /**
     * Creates a Commission Rule (it starts active).
     *
     * @param request the rule data
     * @return 201 Created with the new rule id
     */
    @PostMapping
    public ResponseEntity<CommissionRuleResponse> create(@Valid @RequestBody CreateCommissionRuleRequest request) {
        UUID id = ruleService.create(
                toCommand(request), userContext.currentUserId(), commissionProperties.safeMaxPercentage());
        return ResponseEntity.created(URI.create("/api/commission/rules/" + id))
                .body(new CommissionRuleResponse(id, true));
    }

    /**
     * Lists the Commission Rules (newest first). By default only active rules; pass {@code includeInactive=true} to
     * include inactive ones.
     *
     * @param includeInactive whether to include inactive rules
     * @return the rule list items
     */
    @GetMapping
    public List<CommissionRuleListItem> list(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return ruleService.list(includeInactive);
    }

    /**
     * Full detail of a Commission Rule.
     *
     * @param id the rule id
     * @return the rule detail
     */
    @GetMapping("/{id}")
    public CommissionRuleDetail detail(@PathVariable UUID id) {
        return ruleService.detail(id);
    }

    /**
     * Updates a Commission Rule's editable fields.
     *
     * @param id the rule id
     * @param request the new data
     * @return the refreshed rule detail
     */
    @PutMapping("/{id}")
    public CommissionRuleDetail update(@PathVariable UUID id, @Valid @RequestBody CreateCommissionRuleRequest request) {
        return ruleService.update(
                id, toCommand(request), userContext.currentUserId(), commissionProperties.safeMaxPercentage());
    }

    /**
     * Activates a Commission Rule (it becomes usable for new commission calculation).
     *
     * @param id the rule id
     * @return the refreshed rule detail
     */
    @PostMapping("/{id}/activate")
    public CommissionRuleDetail activate(@PathVariable UUID id) {
        return ruleService.activate(id, userContext.currentUserId());
    }

    /**
     * Deactivates a Commission Rule (kept for history; not used for new calculation).
     *
     * @param id the rule id
     * @return the refreshed rule detail
     */
    @PostMapping("/{id}/deactivate")
    public CommissionRuleDetail deactivate(@PathVariable UUID id) {
        return ruleService.deactivate(id, userContext.currentUserId());
    }

    private static CreateCommissionRuleCommand toCommand(CreateCommissionRuleRequest request) {
        return new CreateCommissionRuleCommand(
                request.name(),
                request.percentage(),
                request.targetType(),
                request.targetUserId(),
                request.startDate(),
                request.endDate(),
                request.notes(),
                request.allowAboveLimit() != null && request.allowAboveLimit());
    }
}
