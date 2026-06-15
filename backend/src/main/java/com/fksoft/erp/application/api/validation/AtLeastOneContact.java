package com.fksoft.erp.application.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Class-level constraint: at least one of phone, WhatsApp or e-mail must be provided. */
@Documented
@Constraint(validatedBy = AtLeastOneContactValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AtLeastOneContact {

    String message() default "Informe ao menos um contato (telefone, WhatsApp ou e-mail)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
