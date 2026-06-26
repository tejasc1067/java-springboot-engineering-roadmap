package com.example;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, LoanPeriod> {

    @Override
    public boolean isValid(LoanPeriod period, ConstraintValidatorContext context) {
        // null object, or either bound absent -> nothing to compare. Let @NotNull/@Valid handle
        // presence; a cross-field rule only fires when both values are actually there.
        if (period == null || period.startDate() == null || period.endDate() == null) {
            return true;
        }
        if (!period.endDate().isBefore(period.startDate())) {
            return true;
        }
        // By default a class-level violation is reported against the whole object (path = "loanPeriod"),
        // which tells the client nothing about WHICH field is wrong. Re-attach it to "endDate" so the
        // fieldErrors entry names the offending field. disableDefaultConstraintViolation() suppresses
        // the object-level one; otherwise you'd get two violations.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("endDate")
                .addConstraintViolation();
        return false;
    }
}
