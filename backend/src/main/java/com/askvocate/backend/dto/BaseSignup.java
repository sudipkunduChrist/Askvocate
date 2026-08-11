package com.askvocate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BaseSignup {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email or Phone is required")
    private String emailOrPhone;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;
}
