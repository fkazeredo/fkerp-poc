package com.fksoft.erp.infra.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for the framework-failure mappings: last-resort Bean Validation (entity/method level)
 * and database integrity violations both surface as the standard {@code {code,message,fields}} body.
 */
class GlobalExceptionHandlerTest {

    private final MessageSource messages = new MessageSource() {
        @Override
        public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
            return defaultMessage;
        }

        @Override
        public String getMessage(String code, Object[] args, Locale locale) {
            return code;
        }

        @Override
        public String getMessage(MessageSourceResolvable resolvable, Locale locale) {
            return "";
        }
    };

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messages, new HttpErrorMapping());

    @Test
    void mapsEntityConstraintViolationToBadRequestWithField() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<Sample>> violations = validator.validate(new Sample(null));
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(ex, Locale.getDefault());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("validation.failed");
        assertThat(response.getBody().fields())
                .extracting(ApiErrorResponse.FieldError::field)
                .contains("name");
    }

    @Test
    void mapsDataIntegrityViolationToConflictWithoutLeakingDetails() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("constraint chk_leads_status violated: secret SQL detail");

        ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrity(ex, Locale.getDefault());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("data.integrity-violation");
        assertThat(response.getBody().fields()).isEmpty();
        assertThat(response.getBody().message()).doesNotContain("chk_leads_status", "SQL");
    }

    private record Sample(@NotBlank String name) {}
}
