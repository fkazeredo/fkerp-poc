package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when marking a Proposal as sent with an unknown or inactive SendingChannel. */
public class SendingChannelNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SendingChannelNotAvailableException() {
        super("proposal.sending-channel-not-available");
    }
}
