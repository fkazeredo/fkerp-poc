package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.sales.model.ProposalRejectionReason;
import com.fksoft.erp.domain.sales.service.ProposalRejectionReasonService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the ProposalRejectionReason cadastro. */
@RestController
@RequestMapping("/api/sales/proposal-rejection-reasons")
public class ProposalRejectionReasonController extends AbstractReferenceController<ProposalRejectionReason> {

    public ProposalRejectionReasonController(ProposalRejectionReasonService service) {
        super(service);
    }
}
