package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.Origin;
import com.fksoft.erp.domain.crm.repository.OriginRepository;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link Origin} cadastro. */
@Service
public class OriginService extends AbstractReferenceDataService<Origin> {

    public OriginService(OriginRepository repository) {
        super(repository, command -> Origin.create(command.code(), command.label(), command.sortOrder()));
    }
}
