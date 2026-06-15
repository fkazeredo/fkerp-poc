package com.fksoft.erp.domain.crm;

import org.springframework.stereotype.Service;

/** CRUD service for the {@link InteractionType} cadastro. */
@Service
public class InteractionTypeService extends AbstractReferenceDataService<InteractionType> {

    public InteractionTypeService(InteractionTypeRepository repository) {
        super(repository, command -> InteractionType.create(command.code(), command.label(), command.sortOrder()));
    }
}
