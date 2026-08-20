package com.askvocate.backend.model;

/**
 * Embedded sub-document storing a reference to a single image on Cloudinary.
 * 
 * <p>No raw image bytes are stored in MongoDB — only the Cloudinary
 * {@code publicId} (for management operations) and the HTTPS {@code secureUrl}.
 */
public class CloudinaryRef {

    /** Cloudinary public ID used for asset management / deletion. */
    private String publicId;

    /** HTTPS delivery URL for the uploaded image. */
    private String secureUrl;

    /** Label identifying the image side, e.g. "front" or "back". */
    private String label;

    public CloudinaryRef() {
    }

    public CloudinaryRef(String publicId, String secureUrl, String label) {
        this.publicId = publicId;
        this.secureUrl = secureUrl;
        this.label = label;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getSecureUrl() {
        return secureUrl;
    }

    public void setSecureUrl(String secureUrl) {
        this.secureUrl = secureUrl;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
