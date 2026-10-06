package com.askvocate.backend.model;

/**
 * Lifecycle status of a document verification attempt.
 */
public enum VerificationStatus {
    /** Submitted document or selfie awaiting all required checks. */
    PENDING,

    /** All required checks for this document type passed. */
    VERIFIED,

    /** OCR extraction failed or extracted data did not pass validation. */
    FAILED,

    /** Document was manually rejected (reserved for admin workflows). */
    REJECTED
}
