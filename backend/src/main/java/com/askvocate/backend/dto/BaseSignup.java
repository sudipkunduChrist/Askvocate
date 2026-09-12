package com.askvocate.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.askvocate.backend.entity.AuthProvider;

@Data
public class BaseSignup {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address", regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
    private String emailOrPhone;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    /**
     * How this account authenticates. Defaults to LOCAL when the app doesn't send it,
     * so the existing signup flow keeps working unchanged. GOOGLE is used by the
     * Google sign-up flow (which sends no password).
     */
    private AuthProvider provider = AuthProvider.LOCAL;
}
