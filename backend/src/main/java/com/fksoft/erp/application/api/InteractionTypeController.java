package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.crm.model.InteractionType;
import com.fksoft.erp.domain.crm.service.InteractionTypeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the InteractionType cadastro. */
@RestController
@RequestMapping("/api/crm/interaction-types")
public class InteractionTypeController extends AbstractReferenceController<InteractionType> {

    public InteractionTypeController(InteractionTypeService service) {
        super(service);
    }
}
