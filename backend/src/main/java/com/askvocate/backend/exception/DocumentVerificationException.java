package com.askvocate.backend.exception;

/**
 * Thrown when document verification fails due to invalid input,
 * unsupported document types, or business-rule violations.
 */
public class DocumentVerificationException extends RuntimeException {

    public DocumentVerificationException(String message) {
        super(message);
    }

    public DocumentVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
