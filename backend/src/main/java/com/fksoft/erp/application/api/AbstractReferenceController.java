package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.ReferenceCreateRequest;
import com.fksoft.erp.application.api.dto.ReferenceResponse;
import com.fksoft.erp.application.api.dto.ReferenceUpdateRequest;
import com.fksoft.erp.domain.crm.dto.ReferenceCommand;
import com.fksoft.erp.domain.crm.model.ReferenceData;
import com.fksoft.erp.domain.crm.service.AbstractReferenceDataService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Shared CRUD endpoints for a CRM reference-data cadastro. Reading requires authentication; writing
 * requires the {@code crm:reference:manage} scope (enforced by the security config). Delete is a
 * soft delete (deactivate).
 *
 * @param <T> the reference-data type
 */
public abstract class AbstractReferenceController<T extends ReferenceData> {

    private final AbstractReferenceDataService<T> service;

    protected AbstractReferenceController(AbstractReferenceDataService<T> service) {
        this.service = service;
    }

    /**
     * Lists values ordered by sort order.
     *
     * @param includeInactive whether to include inactive values
     * @return the values
     */
    @GetMapping
    public List<ReferenceResponse> list(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive).stream()
                .map(ReferenceResponse::from)
                .toList();
    }

    /**
     * Gets a value by id.
     *
     * @param id the id
     * @return the value
     */
    @GetMapping("/{id}")
    public ReferenceResponse get(@PathVariable UUID id) {
        return ReferenceResponse.from(service.get(id));
    }

    /**
     * Creates a new value.
     *
     * @param request the data
     * @return 201 with the new value
     */
    @PostMapping
    public ResponseEntity<ReferenceResponse> create(@Valid @RequestBody ReferenceCreateRequest request) {
        UUID id = service.create(new ReferenceCommand(request.code(), request.label(), request.sortOrder()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(ReferenceResponse.from(service.get(id)));
    }

    /**
     * Updates a value (label, sort order, active).
     *
     * @param id the id
     * @param request the data
     * @return the updated value
     */
    @PutMapping("/{id}")
    public ReferenceResponse update(@PathVariable UUID id, @Valid @RequestBody ReferenceUpdateRequest request) {
        service.update(id, request.label(), request.sortOrder(), request.active());
        return ReferenceResponse.from(service.get(id));
    }

    /**
     * Soft-deletes (deactivates) a value.
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
