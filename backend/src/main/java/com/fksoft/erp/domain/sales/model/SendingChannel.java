package com.fksoft.erp.domain.sales.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reference data: channel through which an approved Proposal was sent/presented to the client (managed
 * cadastro). Informational only — marking a Proposal as sent triggers no real integration.
 */
@Entity
@Table(name = "sending_channels")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SendingChannel extends ReferenceData {

    /**
     * Creates a new active SendingChannel.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new SendingChannel
     */
    public static SendingChannel create(String code, String label, int sortOrder) {
        SendingChannel channel = new SendingChannel();
        channel.init(code, label, sortOrder);
        return channel;
    }
}
