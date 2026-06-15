package com.fksoft.erp.infra.web;

import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.error.ErrorDetails;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates Bean Validation failures and {@link DomainException}s into a predictable
 * {@link ApiErrorResponse}. Messages are resolved via the application {@link MessageSource} (pt-BR).
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messages;
    private final HttpErrorMapping httpErrorMapping;

    /**
     * Maps request-body validation errors to HTTP 400 with per-field details.
     *
     * @param ex the validation exception
     * @param locale the request locale
     * @return 400 response with field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, Locale locale) {
        List<ApiErrorResponse.FieldError> fields = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.add(new ApiErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()));
        }
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(ge -> fields.add(new ApiErrorResponse.FieldError(ge.getObjectName(), ge.getDefaultMessage())));
        String message = messages.getMessage("validation.failed", null, "Falha de validação", locale);
        return ResponseEntity.badRequest().body(new ApiErrorResponse("validation.failed", message, fields));
    }

    /**
     * Maps a {@link DomainException} to its configured HTTP status and i18n message.
     *
     * @param ex the domain exception
     * @param locale the request locale
     * @return response with the mapped status and error body
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(DomainException ex, Locale locale) {
        HttpStatus status = httpErrorMapping.statusFor(ex);
        String message = messages.getMessage(ex.code(), ex.args(), ex.code(), locale);
        List<ApiErrorResponse.FieldError> fields = new ArrayList<>();
        if (ex instanceof ErrorDetails ed) {
            ed.details().forEach((k, v) -> fields.add(new ApiErrorResponse.FieldError(k, v)));
        }
        return ResponseEntity.status(status).body(new ApiErrorResponse(ex.code(), message, fields));
    }

    /**
     * Maps a last-resort Bean Validation failure (entity validation on flush or method-level
     * validation, thrown outside request-body binding) to HTTP 400 with per-field details, mirroring
     * {@link #handleValidation}. This keeps the entity validation guard (§5.5) from leaking as a 500.
     *
     * @param ex the constraint violation exception
     * @param locale the request locale
     * @return 400 response with field errors
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, Locale locale) {
        List<ApiErrorResponse.FieldError> fields = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            fields.add(new ApiErrorResponse.FieldError(
                    leafField(violation.getPropertyPath().toString()), violation.getMessage()));
        }
        String message = messages.getMessage("validation.failed", null, "Falha de validação", locale);
        return ResponseEntity.badRequest().body(new ApiErrorResponse("validation.failed", message, fields));
    }

    /**
     * Maps a database integrity violation (a CHECK, UNIQUE or FK constraint reached as a last-resort
     * guard) to HTTP 409 with a safe generic message. The underlying cause is logged server-side but
     * never exposed - no SQL, column or constraint name leaks to the client.
     *
     * @param ex the data integrity exception
     * @param locale the request locale
     * @return 409 response with the standard error body
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, Locale locale) {
        log.warn("Database integrity violation reached the boundary", ex);
        String message =
                messages.getMessage("data.integrity-violation", null, "Violação de integridade dos dados", locale);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("data.integrity-violation", message, List.of()));
    }

    private static String leafField(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }
}
