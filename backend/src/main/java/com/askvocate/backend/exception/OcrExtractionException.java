package com.askvocate.backend.exception;

/**
 * Thrown when OCR text extraction fails or produces results that
 * cannot be parsed into the expected document structure.
 */
public class OcrExtractionException extends RuntimeException {

    public OcrExtractionException(String message) {
        super(message);
    }

    public OcrExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
