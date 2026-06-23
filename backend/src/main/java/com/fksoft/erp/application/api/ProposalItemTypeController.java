package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.ProposalItemTypeCreateRequest;
import com.fksoft.erp.application.api.dto.ProposalItemTypeResponse;
import com.fksoft.erp.application.api.dto.ProposalItemTypeUpdateRequest;
import com.fksoft.erp.domain.sales.service.ProposalItemTypeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * CRUD endpoints for the {@link com.fksoft.erp.domain.sales.model.ProposalItemType} cadastro. A dedicated
 * controller (not the shared {@link AbstractReferenceController}) because the value carries the extra
 * {@code requiresBooking} attribute. Reading requires authentication; writing requires the
 * {@code reference:manage} scope (enforced by the security config for {@code /api/sales/**}).
 */
@RestController
@RequestMapping("/api/sales/proposal-item-types")
public class ProposalItemTypeController {

    private final ProposalItemTypeService service;

    public ProposalItemTypeController(ProposalItemTypeService service) {
        this.service = service;
    }

    /**
     * Lists item types ordered by sort order.
     *
     * @param includeInactive whether to include inactive values
     * @return the values
     */
    @GetMapping
    public List<ProposalItemTypeResponse> list(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive).stream()
                .map(ProposalItemTypeResponse::from)
                .toList();
    }

    /**
     * Gets an item type by id.
     *
     * @param id the id
     * @return the value
     */
    @GetMapping("/{id}")
    public ProposalItemTypeResponse get(@PathVariable UUID id) {
        return ProposalItemTypeResponse.from(service.get(id));
    }

    /**
     * Creates a new item type.
     *
     * @param request the data
     * @return 201 with the new value
     */
    @PostMapping
    public ResponseEntity<ProposalItemTypeResponse> create(@Valid @RequestBody ProposalItemTypeCreateRequest request) {
        UUID id = service.create(request.code(), request.label(), request.sortOrder(), request.requiresBooking());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(ProposalItemTypeResponse.from(service.get(id)));
    }

    /**
     * Updates an item type (label, sort order, active, requiresBooking).
     *
     * @param id the id
     * @param request the data
     * @return the updated value
     */
    @PutMapping("/{id}")
    public ProposalItemTypeResponse update(
            @PathVariable UUID id, @Valid @RequestBody ProposalItemTypeUpdateRequest request) {
        service.update(id, request.label(), request.sortOrder(), request.active(), request.requiresBooking());
        return ProposalItemTypeResponse.from(service.get(id));
    }

    /**
     * Soft-deletes (deactivates) an item type.
     *
     * @param id the id
     * @return 204
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
