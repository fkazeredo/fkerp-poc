package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.crm.Origin;
import com.fksoft.erp.domain.crm.OriginService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the Origin cadastro. */
@RestController
@RequestMapping("/api/crm/origins")
public class OriginController extends AbstractReferenceController<Origin> {

    public OriginController(OriginService service) {
        super(service);
    }
}
