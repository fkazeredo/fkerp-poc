package com.fksoft.erp.domain.financial.service;

import com.fksoft.erp.domain.financial.model.PaymentMethod;
import com.fksoft.erp.domain.financial.repository.PaymentMethodRepository;
import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link PaymentMethod} cadastro. */
@Service
public class PaymentMethodService extends AbstractReferenceDataService<PaymentMethod> {

    public PaymentMethodService(PaymentMethodRepository repository) {
        super(repository, c -> PaymentMethod.create(c.code(), c.label(), c.sortOrder()));
    }
}
