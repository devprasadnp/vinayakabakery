package com.harish.pickles.dto;

import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.User;
import java.time.LocalDateTime;

public final class UserDtos {

    private UserDtos() {
    }

    /** Super admin creating or editing an account. Password is optional on edit. */
    public record UserForm(
            Long id,
            String fullName,
            String email,
            String phone,
            String password,
            Boolean enabled) {
    }

    public record UserResponse(
            Long id,
            String fullName,
            String email,
            String phone,
            Role role,
            String roleLabel,
            boolean enabled,
            LocalDateTime createdAt) {

        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                    u.getRole(), u.getRole().getLabel(), u.isEnabled(), u.getCreatedAt());
        }
    }
}
