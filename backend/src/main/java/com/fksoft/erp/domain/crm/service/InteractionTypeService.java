package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.InteractionType;
import com.fksoft.erp.domain.crm.repository.InteractionTypeRepository;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link InteractionType} cadastro. */
@Service
public class InteractionTypeService extends AbstractReferenceDataService<InteractionType> {

    public InteractionTypeService(InteractionTypeRepository repository) {
        super(repository, command -> InteractionType.create(command.code(), command.label(), command.sortOrder()));
    }
}
