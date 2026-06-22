package com.fksoft.erp.domain.reference;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared CRUD logic for reference-data cadastros (same behavior for every cadastro, any domain). Delete is a
 * soft delete (deactivate); inactive values cannot be used by new records.
 *
 * @param <T> the reference-data type
 */
public abstract class AbstractReferenceDataService<T extends ReferenceData> {

    private final ReferenceDataRepository<T> repository;
    private final Function<ReferenceCommand, T> factory;

    /**
     * @param repository the cadastro repository
     * @param factory builds an entity from a {@link ReferenceCommand}
     */
    protected AbstractReferenceDataService(
            ReferenceDataRepository<T> repository, Function<ReferenceCommand, T> factory) {
        this.repository = repository;
        this.factory = factory;
    }

    /**
     * Lists values ordered by sort order.
     *
     * @param includeInactive whether to include inactive values
     * @return the values
     */
    @Transactional(readOnly = true)
    public List<T> list(boolean includeInactive) {
        return includeInactive
                ? repository.findAllByOrderBySortOrderAsc()
                : repository.findByActiveTrueOrderBySortOrderAsc();
    }

    /**
     * Gets a value by id.
     *
     * @param id the id
     * @return the value
     * @throws ReferenceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public T get(UUID id) {
        return repository.findById(id).orElseThrow(ReferenceNotFoundException::new);
    }

    /**
     * Creates a new value.
     *
     * @param command the data
     * @return the new id
     * @throws DuplicateReferenceCodeException if the code already exists
     */
    @Transactional
    public UUID create(ReferenceCommand command) {
        repository.findByCode(command.code()).ifPresent(existing -> {
            throw new DuplicateReferenceCodeException(command.code());
        });
        T entity = factory.apply(command);
        repository.save(entity);
        return entity.id();
    }

    /**
     * Updates a value's label, sort order and active flag (the code is immutable).
     *
     * @param id the id
     * @param label new label
     * @param sortOrder new sort order
     * @param active new active flag
     */
    @Transactional
    public void update(UUID id, String label, int sortOrder, boolean active) {
        T entity = get(id);
        entity.rename(label);
        entity.reorder(sortOrder);
        if (active) {
            entity.activate();
        } else {
            entity.deactivate();
        }
    }

    /**
     * Soft-deletes (deactivates) a value.
     *
     * @param id the id
     */
    @Transactional
    public void deactivate(UUID id) {
        get(id).deactivate();
    }
}
