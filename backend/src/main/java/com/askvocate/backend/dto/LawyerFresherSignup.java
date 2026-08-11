package com.askvocate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LawyerFresherSignup extends BaseSignup {
    @NotBlank(message = "Name of university is required")
    private String university;

    @NotNull(message = "Graduation year is required")
    private Integer graduationYear;

    private String specialization;

}
