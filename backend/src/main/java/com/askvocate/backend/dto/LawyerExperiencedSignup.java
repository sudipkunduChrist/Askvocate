package com.askvocate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class LawyerExperiencedSignup extends BaseSignup {
    private String university;
    private Integer graduationYear;
    private String specialization;
    private String barCouncilId;
    private List<String> practiceAreas;
    private String currentFirm;
}
