package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.OpportunityLossReason;
import com.fksoft.erp.domain.crm.repository.OpportunityLossReasonRepository;
import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link OpportunityLossReason} cadastro. */
@Service
public class OpportunityLossReasonService extends AbstractReferenceDataService<OpportunityLossReason> {

    public OpportunityLossReasonService(OpportunityLossReasonRepository repository) {
        super(
                repository,
                command -> OpportunityLossReason.create(command.code(), command.label(), command.sortOrder()));
    }
}
