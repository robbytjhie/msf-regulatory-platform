package com.regulatory.platform.exception;

public class ApplicationNotFoundException extends RuntimeException {
    public ApplicationNotFoundException(String message) { super(message); }
}
