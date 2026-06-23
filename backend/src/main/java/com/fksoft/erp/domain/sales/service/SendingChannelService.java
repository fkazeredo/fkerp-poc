package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.reference.AbstractReferenceDataService;
import com.fksoft.erp.domain.sales.model.SendingChannel;
import com.fksoft.erp.domain.sales.repository.SendingChannelRepository;
import org.springframework.stereotype.Service;

/** CRUD service for the {@link SendingChannel} cadastro. */
@Service
public class SendingChannelService extends AbstractReferenceDataService<SendingChannel> {

    public SendingChannelService(SendingChannelRepository repository) {
        super(repository, c -> SendingChannel.create(c.code(), c.label(), c.sortOrder()));
    }
}
