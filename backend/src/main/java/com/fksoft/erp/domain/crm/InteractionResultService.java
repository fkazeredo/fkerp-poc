package com.fksoft.erp.domain.crm;

import org.springframework.stereotype.Service;

/** CRUD service for the {@link InteractionResult} cadastro. */
@Service
public class InteractionResultService extends AbstractReferenceDataService<InteractionResult> {

    public InteractionResultService(InteractionResultRepository repository) {
        super(repository, command -> InteractionResult.create(command.code(), command.label(), command.sortOrder()));
    }
}
