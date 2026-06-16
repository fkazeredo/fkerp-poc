package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.crm.model.InteractionResult;
import com.fksoft.erp.domain.crm.service.InteractionResultService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the InteractionResult cadastro. */
@RestController
@RequestMapping("/api/crm/interaction-results")
public class InteractionResultController extends AbstractReferenceController<InteractionResult> {

    public InteractionResultController(InteractionResultService service) {
        super(service);
    }
}
