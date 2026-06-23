package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import com.fksoft.erp.domain.reference.DuplicateReferenceCodeException;
import com.fksoft.erp.domain.reference.ReferenceData;
import com.fksoft.erp.domain.sales.exception.ProposalItemTypeNotAvailableException;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import com.fksoft.erp.domain.sales.repository.ProposalItemTypeRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD service for the {@link ProposalItemType} cadastro. Reuses the shared reference-data logic for
 * list/get/deactivate but adds the cadastro's extra {@code requiresBooking} attribute to create/update, plus
 * {@link #requireActive(UUID)} for the referencing item flows (the type must exist and be active).
 */
@Service
public class ProposalItemTypeService extends AbstractReferenceDataService<ProposalItemType> {

    private final ProposalItemTypeRepository repository;

    public ProposalItemTypeService(ProposalItemTypeRepository repository) {
        super(repository, c -> ProposalItemType.create(c.code(), c.label(), c.sortOrder(), false));
        this.repository = repository;
    }

    /**
     * Creates a new item type with its booking-requirement flag.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @param requiresBooking whether items of this type require a booking operation
     * @return the new id
     * @throws DuplicateReferenceCodeException if the code already exists
     */
    @Transactional
    public UUID create(String code, String label, int sortOrder, boolean requiresBooking) {
        repository.findByCode(code).ifPresent(existing -> {
            throw new DuplicateReferenceCodeException(code);
        });
        ProposalItemType type = ProposalItemType.create(code, label, sortOrder, requiresBooking);
        repository.save(type);
        return type.id();
    }

    /**
     * Updates an item type's label, sort order, active flag and booking-requirement flag (the code is immutable).
     *
     * @param id the id
     * @param label new label
     * @param sortOrder new sort order
     * @param active new active flag
     * @param requiresBooking new booking-requirement flag
     */
    @Transactional
    public void update(UUID id, String label, int sortOrder, boolean active, boolean requiresBooking) {
        ProposalItemType type = get(id);
        type.rename(label);
        type.reorder(sortOrder);
        if (active) {
            type.activate();
        } else {
            type.deactivate();
        }
        type.changeRequiresBooking(requiresBooking);
    }

    /**
     * Resolves an active item type by id for a referencing flow.
     *
     * @param id the item-type id
     * @return the active item type
     * @throws ProposalItemTypeNotAvailableException if unknown or inactive
     */
    @Transactional(readOnly = true)
    public ProposalItemType requireActive(UUID id) {
        return repository
                .findById(id)
                .filter(ReferenceData::active)
                .orElseThrow(ProposalItemTypeNotAvailableException::new);
    }
}
