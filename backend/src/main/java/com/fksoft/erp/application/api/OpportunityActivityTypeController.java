package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.crm.model.OpportunityActivityType;
import com.fksoft.erp.domain.crm.service.OpportunityActivityTypeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the OpportunityActivityType cadastro. */
@RestController
@RequestMapping("/api/crm/opportunity-activity-types")
public class OpportunityActivityTypeController extends AbstractReferenceController<OpportunityActivityType> {

    public OpportunityActivityTypeController(OpportunityActivityTypeService service) {
        super(service);
    }
}
