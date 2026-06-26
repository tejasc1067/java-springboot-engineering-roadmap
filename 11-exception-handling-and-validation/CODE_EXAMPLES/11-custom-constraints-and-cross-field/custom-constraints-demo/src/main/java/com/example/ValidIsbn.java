package com.example;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

// A field/parameter constraint. @Constraint wires it to the validator that does the work.
// message/groups/payload are the three members Bean Validation requires on every constraint.
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = IsbnValidator.class)
public @interface ValidIsbn {

    String message() default "must be a valid ISBN-13";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
