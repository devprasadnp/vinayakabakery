package com.harish.pickles.service;

import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.UserDtos.UserForm;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.exception.NotFoundException;
import com.harish.pickles.repository.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account management for admins and super admins.
 *
 * <p>Who may touch whom is decided here, not just in the controllers, because both an admin
 * screen and a super-admin screen call this service:</p>
 * <table>
 *   <caption>Permissions by acting role</caption>
 *   <tr><th>Action</th><th>Admin</th><th>Super admin</th></tr>
 *   <tr><td>Reset a customer's password</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>Create, edit, block, reset a delivery partner</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>Create, edit or block a customer</td><td>no</td><td>yes</td></tr>
 *   <tr><td>Touch any admin or super admin account</td><td>no</td><td>yes</td></tr>
 * </table>
 *
 * <p>No method here can change anyone's role, and nobody can lock themselves out of their own
 * account. Promoting or demoting staff stays a database-level decision.</p>
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MIN_PASSWORD_LENGTH = 8;
    /** bcrypt only reads the first 72 bytes, so anything longer would be silently truncated. */
    private static final int MAX_PASSWORD_LENGTH = 72;
    private static final Pattern MOBILE = Pattern.compile("^[6-9]\\d{9}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ----------------------------------------------------------- permissions

    /** Reset a password: any account for a super admin, customers and delivery partners for an admin. */
    public static boolean canResetPassword(User actor, User target) {
        if (actor.getRole() == Role.SUPER_ADMIN) {
            return true;
        }
        return actor.getRole() == Role.ADMIN
                && (target.getRole() == Role.USER || target.getRole() == Role.DELIVERY_PARTNER
                || target.getRole() == Role.STORE_MANAGER);
    }

    /** Edit details or block sign-in: any account for a super admin, delivery partners only for an admin. */
    public static boolean canEditDetails(User actor, User target) {
        if (actor.getRole() == Role.SUPER_ADMIN) {
            return true;
        }
        return actor.getRole() == Role.ADMIN
                && (target.getRole() == Role.DELIVERY_PARTNER || target.getRole() == Role.STORE_MANAGER);
    }

    private static void require(boolean allowed, String what) {
        if (!allowed) {
            throw new AccessDeniedException("You are not allowed to " + what);
        }
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Page<User> list(List<Role> roles, String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return userRepository.findByRoleInOrderByIdDesc(roles, pageable);
        }
        return userRepository.search(roles, query.trim(), pageable);
    }

    /** Loads an account for a management screen, refusing any the acting user has no business seeing. */
    @Transactional(readOnly = true)
    public User getForManagement(Long id, User actingAs) {
        User user = getById(id);
        require(canResetPassword(actingAs, user) || canEditDetails(actingAs, user), "manage that account");
        return user;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("Account", id));
    }

    @Transactional(readOnly = true)
    public long countCustomers() {
        return userRepository.countByRole(Role.USER);
    }

    // ----------------------------------------------------------------- write

    /** Creates a customer account. Super admin only; staff accounts are not created from here. */
    @Transactional
    public User createCustomer(UserForm form, User actingAs) {
        require(actingAs.getRole() == Role.SUPER_ADMIN, "create customer accounts");
        User saved = create(form, Role.USER);
        log.info("{} created customer {}", actingAs.getEmail(), saved.getEmail());
        return saved;
    }

    /** Creates a delivery partner who can sign in and see the orders assigned to them. */
    @Transactional
    public User createDeliveryPartner(UserForm form, User actingAs) {
        require(actingAs.getRole() == Role.ADMIN || actingAs.getRole() == Role.SUPER_ADMIN,
                "create delivery partners");
        User saved = create(form, Role.DELIVERY_PARTNER);
        log.info("{} created delivery partner {}", actingAs.getEmail(), saved.getEmail());
        return saved;
    }

    /** Creates a store manager: runs inventory and orders, nothing else. Admin or super admin only. */
    @Transactional
    public User createStoreManager(UserForm form, User actingAs) {
        require(actingAs.getRole() == Role.ADMIN || actingAs.getRole() == Role.SUPER_ADMIN,
                "create store managers");
        User saved = create(form, Role.STORE_MANAGER);
        log.info("{} created store manager {}", actingAs.getEmail(), saved.getEmail());
        return saved;
    }

    private User create(UserForm form, Role role) {
        String email = normaliseEmail(form.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("An account with " + email + " already exists");
        }
        requireText(form.fullName(), "Full name is required");
        requirePassword(form.password());
        requirePhoneIfPartner(role, form.phone());

        User user = new User(form.fullName().trim(), email, passwordEncoder.encode(form.password()), role);
        user.setPhone(blankToNull(form.phone()));
        user.setEnabled(form.enabled() == null || form.enabled());
        return userRepository.save(user);
    }

    /** Edits name, email, phone and enabled state. Never the role, never the password. */
    @Transactional
    public User updateDetails(Long id, UserForm form, User actingAs) {
        User user = getById(id);
        require(canEditDetails(actingAs, user), "edit that account");
        String email = normaliseEmail(form.email());
        requireText(form.fullName(), "Full name is required");
        requirePhoneIfPartner(user.getRole(), form.phone());

        if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Another account already uses " + email);
        }

        boolean enabled = form.enabled() == null || form.enabled();
        if (!enabled && user.getId().equals(actingAs.getId())) {
            throw new BusinessException("You cannot disable your own account");
        }

        user.setFullName(form.fullName().trim());
        user.setEmail(email);
        user.setPhone(blankToNull(form.phone()));
        user.setEnabled(enabled);
        log.info("{} updated account {}", actingAs.getEmail(), user.getEmail());
        return userRepository.save(user);
    }

    /** Sets a password chosen by the person acting. */
    @Transactional
    public User resetPassword(Long id, String newPassword, User actingAs) {
        User user = getById(id);
        require(canResetPassword(actingAs, user), "reset that account's password");
        requirePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        log.info("{} reset the password for {}", actingAs.getEmail(), user.getEmail());
        return userRepository.save(user);
    }

    /**
     * Sets a strong random password and returns it in plain text, once, so it can be passed
     * on. It is never stored or logged in readable form.
     */
    @Transactional
    public String resetPasswordToGenerated(Long id, User actingAs) {
        String generated = generatePassword();
        resetPassword(id, generated, actingAs);
        return generated;
    }

    @Transactional
    public User setEnabled(Long id, boolean enabled, User actingAs) {
        User user = getById(id);
        require(canEditDetails(actingAs, user), "change that account's sign-in access");
        if (!enabled && user.getId().equals(actingAs.getId())) {
            throw new BusinessException("You cannot disable your own account");
        }
        user.setEnabled(enabled);
        return userRepository.save(user);
    }

    // --------------------------------------------------------------- helpers

    public static String generatePassword() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static void requirePassword(String password) {
        if (password == null || password.trim().length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException("Password must be " + MAX_PASSWORD_LENGTH + " characters or fewer");
        }
    }

    /** A courier without a phone number is no use to the customer waiting on a doorstep. */
    private static void requirePhoneIfPartner(Role role, String phone) {
        if (role == Role.DELIVERY_PARTNER && (phone == null || !MOBILE.matcher(phone.trim()).matches())) {
            throw new BusinessException("Enter the delivery partner's 10 digit mobile number");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
    }

    private static String normaliseEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new BusinessException("Enter a valid email address");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
