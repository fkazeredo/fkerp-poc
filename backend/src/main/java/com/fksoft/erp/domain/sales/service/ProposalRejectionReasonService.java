package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import com.fksoft.erp.domain.sales.model.ProposalRejectionReason;
import com.fksoft.erp.domain.sales.repository.ProposalRejectionReasonRepository;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link ProposalRejectionReason} cadastro. */
@Service
public class ProposalRejectionReasonService extends AbstractReferenceDataService<ProposalRejectionReason> {

    public ProposalRejectionReasonService(ProposalRejectionReasonRepository repository) {
        super(repository, c -> ProposalRejectionReason.create(c.code(), c.label(), c.sortOrder()));
    }
}
