package com.fksoft.erp.infra.web;

import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.error.ErrorDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
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
}
