package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.crm.model.LossReason;
import com.fksoft.erp.domain.crm.service.LossReasonService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the LossReason cadastro. */
@RestController
@RequestMapping("/api/crm/loss-reasons")
public class LossReasonController extends AbstractReferenceController<LossReason> {

    public LossReasonController(LossReasonService service) {
        super(service);
    }
}
