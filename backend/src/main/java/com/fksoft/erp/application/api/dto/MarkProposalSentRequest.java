package com.fksoft.erp.application.api.dto;

import java.util.UUID;

/**
 * Request body to mark an approved Proposal as sent to the client. The sending-channel cadastro id is optional
 * descriptive information — it may be {@code null}. Marking as sent does not trigger any real
 * e-mail/WhatsApp/phone integration.
 *
 * @param channelId the descriptive sending-channel cadastro id, or {@code null} (the channel is optional)
 */
public record MarkProposalSentRequest(UUID channelId) {}
