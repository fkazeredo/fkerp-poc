package com.fksoft.erp.application.api.validation;

import com.fksoft.erp.application.api.dto.LeadCreateRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Validates {@link AtLeastOneContact} on a {@link LeadCreateRequest}. */
public class AtLeastOneContactValidator implements ConstraintValidator<AtLeastOneContact, LeadCreateRequest> {

    @Override
    public boolean isValid(LeadCreateRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return hasText(value.phone()) || hasText(value.whatsapp()) || hasText(value.email());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
