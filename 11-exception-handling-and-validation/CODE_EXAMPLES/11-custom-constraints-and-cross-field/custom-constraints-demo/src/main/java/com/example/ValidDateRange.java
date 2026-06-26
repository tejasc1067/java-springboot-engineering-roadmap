package com.example;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

// A class-level (cross-field) constraint. @Target(TYPE) means it annotates the whole object,
// not a single field — that's how the validator gets to see startDate AND endDate together.
@Target(TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
public @interface ValidDateRange {

    String message() default "endDate must not be before startDate";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
