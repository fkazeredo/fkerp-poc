package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.commission.model.CommissionResolutionReason;
import com.fksoft.erp.domain.commission.service.CommissionResolutionReasonService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the CommissionResolutionReason cadastro (shared by the reject and cancel actions). */
@RestController
@RequestMapping("/api/commission/resolution-reasons")
public class CommissionResolutionReasonController extends AbstractReferenceController<CommissionResolutionReason> {

    public CommissionResolutionReasonController(CommissionResolutionReasonService service) {
        super(service);
    }
}
