package com.harish.pickles.web.rest;

import com.harish.pickles.domain.User;
import com.harish.pickles.dto.AuthDtos.AuthResponse;
import com.harish.pickles.dto.AuthDtos.ForgotPasswordRequest;
import com.harish.pickles.dto.AuthDtos.LoginRequest;
import com.harish.pickles.dto.AuthDtos.RegisterRequest;
import com.harish.pickles.dto.AuthDtos.ResetPasswordRequest;
import com.harish.pickles.service.AuthService;
import com.harish.pickles.service.PasswordResetService;
import com.harish.pickles.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthRestController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthRestController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    @Operation(summary = "Create a customer account and return a JWT")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(201).body(authService.toAuthResponse(user));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email + password for a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Email a one-time password reset link. Always answers 202, whether or not the address has an account.")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().body(Map.of("message",
                "If an account exists for that email, a reset link is on its way."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Choose a new password using the token from the reset email")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.completeReset(request.token(), request.newPassword());
        return Map.of("status", "reset");
    }

    @GetMapping("/me")
    @Operation(summary = "Profile of the signed-in user")
    public Map<String, Object> me() {
        User user = CurrentUser.require();
        return Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().name());
    }
}
