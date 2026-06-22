package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.crm.model.OpportunityLossReason;
import com.fksoft.erp.domain.crm.service.OpportunityLossReasonService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the OpportunityLossReason cadastro. */
@RestController
@RequestMapping("/api/crm/opportunity-loss-reasons")
public class OpportunityLossReasonController extends AbstractReferenceController<OpportunityLossReason> {

    public OpportunityLossReasonController(OpportunityLossReasonService service) {
        super(service);
    }
}
