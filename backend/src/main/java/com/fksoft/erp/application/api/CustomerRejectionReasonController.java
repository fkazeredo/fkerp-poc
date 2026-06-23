package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.sales.model.CustomerRejectionReason;
import com.fksoft.erp.domain.sales.service.CustomerRejectionReasonService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the CustomerRejectionReason cadastro. */
@RestController
@RequestMapping("/api/sales/customer-rejection-reasons")
public class CustomerRejectionReasonController extends AbstractReferenceController<CustomerRejectionReason> {

    public CustomerRejectionReasonController(CustomerRejectionReasonService service) {
        super(service);
    }
}
