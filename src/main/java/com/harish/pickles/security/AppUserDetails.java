package com.harish.pickles.security;

import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AppUserDetails implements UserDetails {

    private final transient User user;
    private final List<GrantedAuthority> authorities;

    public AppUserDetails(User user) {
        this.user = user;
        this.authorities = authoritiesFor(user.getRole());
    }

    /**
     * Roles are layered rather than exclusive: a SUPER_ADMIN also holds ROLE_ADMIN and
     * ROLE_USER, an ADMIN also holds ROLE_USER. That keeps every {@code hasRole('ADMIN')}
     * rule working for super admins without needing a RoleHierarchy bean.
     *
     * <p>A DELIVERY_PARTNER holds ROLE_USER plus its own ROLE_DELIVERY_PARTNER and nothing
     * administrative, so it can never reach {@code /admin/**}.</p>
     */
    public static List<GrantedAuthority> authoritiesFor(Role role) {
        List<GrantedAuthority> granted = new ArrayList<>();
        granted.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (role == Role.DELIVERY_PARTNER) {
            granted.add(new SimpleGrantedAuthority("ROLE_DELIVERY_PARTNER"));
        }
        // Holds ROLE_USER too, so a store manager can place orders through the normal storefront checkout.
        if (role == Role.STORE_MANAGER) {
            granted.add(new SimpleGrantedAuthority("ROLE_STORE_MANAGER"));
        }
        if (role == Role.ADMIN || role == Role.SUPER_ADMIN) {
            granted.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        if (role == Role.SUPER_ADMIN) {
            granted.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        }
        return List.copyOf(granted);
    }

    public User getUser() {
        return user;
    }

    public Long getId() {
        return user.getId();
    }

    public String getFullName() {
        return user.getFullName();
    }

    public Role getRole() {
        return user.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }
}
