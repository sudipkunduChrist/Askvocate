package com.askvocate.backend.exception;

/**
 * Thrown when document verification fails due to invalid input,
 * unsupported document types, or business-rule violations.
 */
public class DocumentVerificationException extends RuntimeException {

    private final String file;

    public DocumentVerificationException(String message) {
        super(message);
        this.file = null;
    }

    public DocumentVerificationException(String message, Throwable cause) {
        super(message, cause);
        this.file = null;
    }

    public DocumentVerificationException(String message, Throwable cause, String file) {
        super(message, cause);
        this.file = file;
    }

    public String getFile() {
        return file;
    }
}
