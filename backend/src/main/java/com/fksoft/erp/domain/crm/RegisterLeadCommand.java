package com.fksoft.erp.domain.crm;

import java.util.UUID;

/**
 * Input for registering a Lead. Built by the delivery layer after boundary validation.
 *
 * @param name interested person or company name (required)
 * @param phone phone (digits) or null
 * @param whatsapp WhatsApp (digits) or null
 * @param email e-mail or null
 * @param originId id of the (active) Origin cadastro value (required)
 * @param responsiblePersonId optional user id of the responsible person
 * @param initialNote optional initial note (recorded as the first interaction)
 */
public record RegisterLeadCommand(
        String name,
        String phone,
        String whatsapp,
        String email,
        UUID originId,
        UUID responsiblePersonId,
        String initialNote) {}
