package com.askvocate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class LawyerExperiencedSignup extends BaseSignup {
    @NotBlank(message = "Name of university is required")
    private String university;

    @NotNull(message = "Graduation year is required")
    private Integer graduationYear;

    private String specialization;

    @NotBlank(message = "Bar Council ID is required")
    private String barCouncilId;  // ✅ Required for experienced lawyers

    private List<String> practiceAreas;

    private String currentFirm;

}
