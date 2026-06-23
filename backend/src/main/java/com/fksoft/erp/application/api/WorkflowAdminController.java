package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.WorkflowAttentionRuleCreateRequest;
import com.fksoft.erp.application.api.dto.WorkflowAttentionRuleUpdateRequest;
import com.fksoft.erp.application.api.dto.WorkflowStateUpdateRequest;
import com.fksoft.erp.domain.workflow.WorkflowAdminService;
import com.fksoft.erp.domain.workflow.WorkflowCatalogView;
import com.fksoft.erp.domain.workflow.WorkflowDetail;
import com.fksoft.erp.domain.workflow.WorkflowSummary;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Workflow administration endpoints (the visual editor + rule panel). All of {@code /api/workflows/**}
 * requires the {@code workflow:manage} scope (enforced by the security config). Exposes the configurable
 * workflows for editing — states, transitions and the attention rules that drive the pending-items
 * worklists — respecting the {@code system} lock on the seeded rows.
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowAdminController {

    private final WorkflowAdminService service;

    public WorkflowAdminController(WorkflowAdminService service) {
        this.service = service;
    }

    /**
     * Lists the configurable workflows.
     *
     * @return the workflow summaries
     */
    @GetMapping
    public List<WorkflowSummary> list() {
        return service.list();
    }

    /**
     * The authoring catalog (attention conditions + guard/post-function keys).
     *
     * @return the catalog
     */
    @GetMapping("/catalog")
    public WorkflowCatalogView catalog() {
        return service.catalog();
    }

    /**
     * The full detail of a workflow (states, transitions, attention rules).
     *
     * @param code the definition code
     * @return the detail
     */
    @GetMapping("/{code}")
    public WorkflowDetail detail(@PathVariable String code) {
        return service.detail(code);
    }

    /**
     * Creates a custom attention rule on a workflow.
     *
     * @param code the definition code
     * @param request the rule data
     * @return 201 with the new rule id
     */
    @PostMapping("/{code}/attention-rules")
    public ResponseEntity<Map<String, UUID>> createAttentionRule(
            @PathVariable String code, @Valid @RequestBody WorkflowAttentionRuleCreateRequest request) {
        UUID id = service.createAttentionRule(
                code,
                request.conditionKey(),
                request.thresholdDays(),
                request.stateValue(),
                request.code(),
                request.label(),
                request.sortOrder());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/workflows/attention-rules/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(Map.of("id", id));
    }

    /**
     * Updates an attention rule (label, threshold, order, active).
     *
     * @param id the rule id
     * @param request the rule data
     * @return 204
     */
    @PutMapping("/attention-rules/{id}")
    public ResponseEntity<Void> updateAttentionRule(
            @PathVariable UUID id, @Valid @RequestBody WorkflowAttentionRuleUpdateRequest request) {
        service.updateAttentionRule(
                id, request.label(), request.thresholdDays(), request.sortOrder(), request.active());
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes a custom attention rule (a system rule is protected).
     *
     * @param id the rule id
     * @return 204
     */
    @DeleteMapping("/attention-rules/{id}")
    public ResponseEntity<Void> deleteAttentionRule(@PathVariable UUID id) {
        service.deleteAttentionRule(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates a workflow state's editable attributes (label, order, active).
     *
     * @param id the state id
     * @param request the state data
     * @return 204
     */
    @PutMapping("/states/{id}")
    public ResponseEntity<Void> updateState(
            @PathVariable UUID id, @Valid @RequestBody WorkflowStateUpdateRequest request) {
        service.updateState(id, request.label(), request.sortOrder(), request.active());
        return ResponseEntity.noContent().build();
    }
}
