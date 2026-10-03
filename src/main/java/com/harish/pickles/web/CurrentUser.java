package com.harish.pickles.web;

import com.harish.pickles.domain.User;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.security.AppUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Small bridge from Spring Security's principal to our {@link User} entity. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static User require() {
        return find().orElseThrow(() -> new BusinessException("Please sign in to continue"));
    }

    public static java.util.Optional<User> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AppUserDetails details)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(details.getUser());
    }
}
