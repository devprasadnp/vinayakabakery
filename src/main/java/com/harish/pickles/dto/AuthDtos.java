package com.harish.pickles.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Enter a valid 10 digit Indian mobile number") String phone,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be at least 8 characters") String password) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String newPassword) {
    }

    public record AuthResponse(
            String token,
            String tokenType,
            long expiresInSeconds,
            String email,
            String fullName,
            String role) {
    }
}
