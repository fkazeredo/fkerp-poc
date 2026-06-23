package com.fksoft.erp.application.api;

import com.fksoft.erp.domain.sales.model.SendingChannel;
import com.fksoft.erp.domain.sales.service.SendingChannelService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for the SendingChannel cadastro. */
@RestController
@RequestMapping("/api/sales/sending-channels")
public class SendingChannelController extends AbstractReferenceController<SendingChannel> {

    public SendingChannelController(SendingChannelService service) {
        super(service);
    }
}
