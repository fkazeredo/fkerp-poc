package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import com.fksoft.erp.domain.sales.model.CustomerRejectionReason;
import com.fksoft.erp.domain.sales.repository.CustomerRejectionReasonRepository;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link CustomerRejectionReason} cadastro. */
@Service
public class CustomerRejectionReasonService extends AbstractReferenceDataService<CustomerRejectionReason> {

    public CustomerRejectionReasonService(CustomerRejectionReasonRepository repository) {
        super(repository, c -> CustomerRejectionReason.create(c.code(), c.label(), c.sortOrder()));
    }
}
