package com.askvocate.backend.model;

/**
 * Lifecycle status of a document verification attempt.
 */
public enum VerificationStatus {
    /** Document uploaded, OCR in progress or pending review. */
    PENDING,

    /** OCR extraction succeeded and data passed validation. */
    VERIFIED,

    /** OCR extraction failed or extracted data did not pass validation. */
    FAILED,

    /** Document was manually rejected (reserved for admin workflows). */
    REJECTED
}
