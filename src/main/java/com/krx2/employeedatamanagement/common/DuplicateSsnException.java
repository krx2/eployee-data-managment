package com.krx2.employeedatamanagement.common;

public class DuplicateSsnException extends RuntimeException {

    public DuplicateSsnException(Throwable cause) {
        super("An employee with this SSN already exists", cause);
    }
}
