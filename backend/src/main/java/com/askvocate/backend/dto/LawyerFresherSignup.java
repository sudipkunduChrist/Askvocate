package com.askvocate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LawyerFresherSignup extends BaseSignup {
    private String university;
    private Integer graduationYear;
    private String specialization;
}
