package com.fksoft.erp.domain.crm;

import org.springframework.stereotype.Service;

/** CRUD service for the {@link LossReason} cadastro. */
@Service
public class LossReasonService extends AbstractReferenceDataService<LossReason> {

    public LossReasonService(LossReasonRepository repository) {
        super(repository, command -> LossReason.create(command.code(), command.label(), command.sortOrder()));
    }
}
