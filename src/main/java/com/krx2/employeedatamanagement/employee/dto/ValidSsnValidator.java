package com.krx2.employeedatamanagement.employee.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidSsnValidator implements ConstraintValidator<ValidSsn, String> {

    private static final Pattern FORMAT = Pattern.compile("(\\d{3})-(\\d{2})-(\\d{4})");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        Matcher matcher = FORMAT.matcher(value);
        if (!matcher.matches()) {
            return false;
        }

        String area = matcher.group(1);
        String group = matcher.group(2);
        String serial = matcher.group(3);

        // Lexicographic comparison is safe here only because the regex above guarantees
        // area is always exactly 3 digits, so "900" <= area <= "999" matches numerically too.
        boolean areaIsInvalid = area.equals("000") || area.equals("666") || area.compareTo("900") >= 0;
        boolean groupIsInvalid = group.equals("00");
        boolean serialIsInvalid = serial.equals("0000");

        return !areaIsInvalid && !groupIsInvalid && !serialIsInvalid;
    }
}
