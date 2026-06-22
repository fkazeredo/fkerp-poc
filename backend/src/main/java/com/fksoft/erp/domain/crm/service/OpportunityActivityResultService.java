package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.OpportunityActivityResult;
import com.fksoft.erp.domain.crm.repository.OpportunityActivityResultRepository;
import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link OpportunityActivityResult} cadastro. */
@Service
public class OpportunityActivityResultService extends AbstractReferenceDataService<OpportunityActivityResult> {

    public OpportunityActivityResultService(OpportunityActivityResultRepository repository) {
        super(
                repository,
                command -> OpportunityActivityResult.create(command.code(), command.label(), command.sortOrder()));
    }
}
