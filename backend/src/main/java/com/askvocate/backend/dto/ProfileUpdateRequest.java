package com.askvocate.backend.dto;

import lombok.Data;
import java.util.List;

/**
 * Data Transfer Object for updating user profiles.
 */
@Data
public class ProfileUpdateRequest {
    private String name;
    private String email;
    private String phone;
    private String address;

    private String university;
    private Integer graduationYear;
    private String specialization;
    private String barCouncilId;
    private List<String> practiceAreas;
    private String currentFirm;
}
