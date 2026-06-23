package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.OpportunityActivityType;
import com.fksoft.erp.domain.crm.repository.OpportunityActivityTypeRepository;
import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link OpportunityActivityType} cadastro. */
@Service
public class OpportunityActivityTypeService extends AbstractReferenceDataService<OpportunityActivityType> {

    public OpportunityActivityTypeService(OpportunityActivityTypeRepository repository) {
        super(
                repository,
                command -> OpportunityActivityType.create(command.code(), command.label(), command.sortOrder()));
    }
}
