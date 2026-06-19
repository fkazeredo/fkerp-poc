package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.sales.model.SendingChannel;

/**
 * Request body to mark an approved Proposal as sent to the client. The sending channel is optional descriptive
 * information (a fixed commercial set) — it may be {@code null}. Marking as sent does not trigger any real
 * e-mail/WhatsApp/phone integration.
 *
 * @param channel the descriptive sending channel, or {@code null} (the channel is optional)
 */
public record MarkProposalSentRequest(SendingChannel channel) {}
