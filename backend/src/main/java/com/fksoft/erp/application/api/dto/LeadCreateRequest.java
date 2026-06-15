package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.application.api.validation.AtLeastOneContact;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Request to create a Lead. Boundary validation: name and origin required, at least one contact,
 * e-mail well-formed, phone/WhatsApp digits only.
 *
 * @param name interested person or company name
 * @param phone phone (digits only) or empty
 * @param whatsapp WhatsApp (digits only) or empty
 * @param email e-mail or empty
 * @param originId id of the chosen origin cadastro value
 * @param responsiblePersonId optional responsible user id
 * @param initialNote optional initial note
 */
@AtLeastOneContact
public record LeadCreateRequest(
        @NotBlank(message = "Nome é obrigatório") String name,
        @Pattern(regexp = "\\d*", message = "Telefone deve conter apenas dígitos") String phone,
        @Pattern(regexp = "\\d*", message = "WhatsApp deve conter apenas dígitos") String whatsapp,
        @Email(message = "E-mail inválido") String email,
        @NotNull(message = "Origem é obrigatória") UUID originId,
        UUID responsiblePersonId,
        String initialNote) {}
