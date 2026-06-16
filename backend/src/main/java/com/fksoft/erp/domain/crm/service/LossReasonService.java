package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.LossReason;
import com.fksoft.erp.domain.crm.repository.LossReasonRepository;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link LossReason} cadastro. */
@Service
public class LossReasonService extends AbstractReferenceDataService<LossReason> {

    public LossReasonService(LossReasonRepository repository) {
        super(repository, command -> LossReason.create(command.code(), command.label(), command.sortOrder()));
    }
}
