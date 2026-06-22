package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.crm.model.OpportunityActivityResult;
import com.fksoft.erp.domain.crm.service.OpportunityActivityResultService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the OpportunityActivityResult cadastro. */
@RestController
@RequestMapping("/api/crm/opportunity-activity-results")
public class OpportunityActivityResultController extends AbstractReferenceController<OpportunityActivityResult> {

    public OpportunityActivityResultController(OpportunityActivityResultService service) {
        super(service);
    }
}
