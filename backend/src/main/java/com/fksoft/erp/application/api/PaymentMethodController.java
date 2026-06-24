package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.financial.model.PaymentMethod;
import com.fksoft.erp.domain.financial.service.PaymentMethodService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the PaymentMethod cadastro. */
@RestController
@RequestMapping("/api/financial/payment-methods")
public class PaymentMethodController extends AbstractReferenceController<PaymentMethod> {

    public PaymentMethodController(PaymentMethodService service) {
        super(service);
    }
}
