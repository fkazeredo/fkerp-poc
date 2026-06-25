package com.fksoft.erp.domain.commission.service;

import com.fksoft.erp.domain.commission.model.CommissionResolutionReason;
import com.fksoft.erp.domain.commission.repository.CommissionResolutionReasonRepository;
import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link CommissionResolutionReason} cadastro. */
@Service
public class CommissionResolutionReasonService extends AbstractReferenceDataService<CommissionResolutionReason> {

    public CommissionResolutionReasonService(CommissionResolutionReasonRepository repository) {
        super(repository, c -> CommissionResolutionReason.create(c.code(), c.label(), c.sortOrder()));
    }
}
