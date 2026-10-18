package com.askvocate.backend.model;

import java.time.Instant;

/**
 * Readable, signature-verified data decoded from an Aadhaar Secure QR.
 *
 * <p>Embedded on {@link UserDocument#getQrData()}. Populated during upload by scanning
 * the card's QR (back side first, then front). The signed QR gates Aadhaar verification:
 * the printed OCR fields must match this data, and a mismatch is routed to manual review.
 */
public class QrData {

    /** Last four digits of the Aadhaar number, as signed inside the QR. */
    private String aadhaarLast4;
    private String name;
    private String dob;
    private String gender;
    private String address;
    private String pincode;
    /** Image side the QR was read from: {@code "front"} or {@code "back"}. */
    private String imageSide;
    /** Name of the UIDAI certificate that validated the signature. */
    private String certificate;
    /** Raw decimal payload read from the QR, exactly as the scanner returned it. */
    private String rawText;
    private Instant scannedAt;

    public QrData() {
    }

    public String getAadhaarLast4() { return aadhaarLast4; }
    public void setAadhaarLast4(String aadhaarLast4) { this.aadhaarLast4 = aadhaarLast4; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getImageSide() { return imageSide; }
    public void setImageSide(String imageSide) { this.imageSide = imageSide; }
    public String getCertificate() { return certificate; }
    public void setCertificate(String certificate) { this.certificate = certificate; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public Instant getScannedAt() { return scannedAt; }
    public void setScannedAt(Instant scannedAt) { this.scannedAt = scannedAt; }
}
