package com.askvocate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BarCouncilVerificationRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Bar Council enrollment number is required")
    private String barCouncilNumber;

    private String advocateName;
    private String stateCouncil;
    private Integer enrollmentYear;
}
