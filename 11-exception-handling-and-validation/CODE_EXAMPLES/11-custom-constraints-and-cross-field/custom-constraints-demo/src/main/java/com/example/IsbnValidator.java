package com.example;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IsbnValidator implements ConstraintValidator<ValidIsbn, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Convention: null is valid here. A constraint validates the SHAPE of a present value;
        // "required or not" is @NotNull/@NotBlank's job. Returning true on null keeps the two
        // concerns separate, so a field can be optional-but-well-formed without this rejecting it.
        if (value == null) {
            return true;
        }
        if (value.length() != 13) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int digit = c - '0';
            // ISBN-13 weights alternate 1,3,1,3,... starting from the first digit.
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        // The 13th digit is a check digit chosen so the weighted sum is a multiple of 10.
        return sum % 10 == 0;
    }
}
