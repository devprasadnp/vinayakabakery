package com.harish.pickles.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/**
 * After a form login, send the person somewhere useful: a page they were bounced from is still
 * honoured first (the parent class does that), otherwise delivery partners land on their own
 * deliveries screen and everyone else on the storefront, as before.
 */
public class RoleAwareLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public static final String DELIVERY_HOME = "/delivery";

    public RoleAwareLoginSuccessHandler() {
        setDefaultTargetUrl("/");
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        boolean partner = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DELIVERY_PARTNER".equals(a.getAuthority()));
        boolean manager = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_STORE_MANAGER".equals(a.getAuthority()));
        if (manager) {
            return "/admin";
        }
        return partner ? DELIVERY_HOME : super.determineTargetUrl(request, response, authentication);
    }
}
