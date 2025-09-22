package com.possystem.backend.common.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = {DowValidator.class})
public @interface DowConstraint {
    String message() default "Invalid date of work";

    int min();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}