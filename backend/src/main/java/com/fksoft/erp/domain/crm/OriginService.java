package com.fksoft.erp.domain.crm;

import org.springframework.stereotype.Service;

/** CRUD service for the {@link Origin} cadastro. */
@Service
public class OriginService extends AbstractReferenceDataService<Origin> {

    public OriginService(OriginRepository repository) {
        super(repository, command -> Origin.create(command.code(), command.label(), command.sortOrder()));
    }
}
