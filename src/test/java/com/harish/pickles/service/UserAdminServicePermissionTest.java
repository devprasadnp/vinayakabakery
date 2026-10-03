package com.harish.pickles.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.UserDtos.UserForm;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The service is shared by the admin and super-admin screens, so the "who may touch whom" rules
 * are pinned here. The one that matters most: an admin must never be able to reset the password
 * of an admin or super admin, because that is a takeover of a more powerful account.
 */
class UserAdminServicePermissionTest {

    private UserRepository users;
    private UserAdminService service;

    private User superAdmin;
    private User admin;
    private User customer;
    private User partner;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UserAdminService(users, encoder);

        superAdmin = account(1L, "super@x.in", Role.SUPER_ADMIN);
        admin = account(2L, "admin@x.in", Role.ADMIN);
        customer = account(3L, "cust@x.in", Role.USER);
        partner = account(4L, "rider@x.in", Role.DELIVERY_PARTNER);
        partner.setPhone("9876543210");
        for (User u : new User[] {superAdmin, admin, customer, partner}) {
            when(users.findById(u.getId())).thenReturn(Optional.of(u));
        }
        when(users.existsByEmailIgnoreCase(any())).thenReturn(false);
    }

    private static User account(long id, String email, Role role) {
        User u = new User("Name " + id, email, "old-hash", role);
        u.setId(id);
        return u;
    }

    private static UserForm form(String name, String email, String phone, String password) {
        return new UserForm(null, name, email, phone, password, true);
    }

    // ------------------------------------------------------------ password reset

    @Test
    void adminCanResetACustomersAndAPartnersPassword() {
        service.resetPassword(customer.getId(), "new-password-1", admin);
        service.resetPassword(partner.getId(), "new-password-2", admin);

        assertThat(customer.getPasswordHash()).isEqualTo("hash:new-password-1");
        assertThat(partner.getPasswordHash()).isEqualTo("hash:new-password-2");
    }

    @Test
    void adminCannotResetAnotherAdminOrTheSuperAdmin() {
        User otherAdmin = account(5L, "admin2@x.in", Role.ADMIN);
        when(users.findById(5L)).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> service.resetPassword(superAdmin.getId(), "hijacked-pass", admin))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.resetPassword(otherAdmin.getId(), "hijacked-pass", admin))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.resetPasswordToGenerated(superAdmin.getId(), admin))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(superAdmin.getPasswordHash()).isEqualTo("old-hash");
        assertThat(otherAdmin.getPasswordHash()).isEqualTo("old-hash");
    }

    @Test
    void superAdminCanResetAnyone() {
        for (User target : new User[] {customer, partner, admin}) {
            assertThatCode(() -> service.resetPassword(target.getId(), "brand-new-pass", superAdmin))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void customersAndPartnersCannotResetAnyonesPassword() {
        assertThatThrownBy(() -> service.resetPassword(customer.getId(), "some-password", customer))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.resetPassword(customer.getId(), "some-password", partner))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void generatedPasswordIsReturnedOnceAndActuallyApplied() {
        String generated = service.resetPasswordToGenerated(customer.getId(), admin);

        assertThat(generated).hasSize(14);
        assertThat(customer.getPasswordHash()).isEqualTo("hash:" + generated);
    }

    @Test
    void passwordLengthLimitsAreEnforced() {
        assertThatThrownBy(() -> service.resetPassword(customer.getId(), "short", admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("at least 8");
        assertThatThrownBy(() -> service.resetPassword(customer.getId(), "x".repeat(73), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("72");
    }

    // ------------------------------------------------------------------- viewing

    @Test
    void adminCannotOpenAnAdminAccountsManagementScreen() {
        assertThatThrownBy(() -> service.getForManagement(superAdmin.getId(), admin))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.getForManagement(customer.getId(), admin)).isSameAs(customer);
        assertThat(service.getForManagement(partner.getId(), admin)).isSameAs(partner);
    }

    // ------------------------------------------------------------ editing details

    @Test
    void adminCanEditAndBlockAPartnerButNotACustomer() {
        service.updateDetails(partner.getId(),
                new UserForm(partner.getId(), "Rider One", "rider@x.in", "9123456789", null, true), admin);
        assertThat(partner.getFullName()).isEqualTo("Rider One");

        service.setEnabled(partner.getId(), false, admin);
        assertThat(partner.isEnabled()).isFalse();

        assertThatThrownBy(() -> service.updateDetails(customer.getId(),
                new UserForm(customer.getId(), "Renamed", "cust@x.in", null, null, true), admin))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.setEnabled(customer.getId(), false, admin))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(customer.isEnabled()).isTrue();
    }

    @Test
    void adminCannotBlockOrEditAnotherAdmin() {
        assertThatThrownBy(() -> service.setEnabled(superAdmin.getId(), false, admin))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.updateDetails(superAdmin.getId(),
                new UserForm(superAdmin.getId(), "Pwned", "pwned@x.in", null, null, true), admin))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nobodyCanBlockTheirOwnAccount() {
        assertThatThrownBy(() -> service.setEnabled(superAdmin.getId(), false, superAdmin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("your own account");
    }

    // ------------------------------------------------------------------ creating

    @Test
    void adminCanCreateSeveralDeliveryPartners() {
        User a = service.createDeliveryPartner(form("Rider A", "a@x.in", "9000000001", "password-a1"), admin);
        User b = service.createDeliveryPartner(form("Rider B", "b@x.in", "9000000002", "password-b2"), admin);

        assertThat(a.getRole()).isEqualTo(Role.DELIVERY_PARTNER);
        assertThat(b.getRole()).isEqualTo(Role.DELIVERY_PARTNER);
        assertThat(a.getPasswordHash()).isEqualTo("hash:password-a1");
        verify(users, org.mockito.Mockito.times(2)).save(any(User.class));
    }

    @Test
    void partnerNeedsAValidMobileNumber() {
        assertThatThrownBy(() -> service.createDeliveryPartner(form("Rider", "r@x.in", null, "password-1"), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("mobile");
        assertThatThrownBy(() -> service.createDeliveryPartner(form("Rider", "r@x.in", "12345", "password-1"), admin))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void duplicateEmailIsRefused() {
        when(users.existsByEmailIgnoreCase("dup@x.in")).thenReturn(true);

        assertThatThrownBy(() -> service.createDeliveryPartner(
                form("Rider", "DUP@x.in", "9000000003", "password-1"), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("already exists");
    }

    @Test
    void onlyASuperAdminCanCreateACustomer() {
        assertThatThrownBy(() -> service.createCustomer(form("New", "new@x.in", null, "password-1"), admin))
                .isInstanceOf(AccessDeniedException.class);
        verify(users, never()).save(any());

        assertThat(service.createCustomer(form("New", "new@x.in", null, "password-1"), superAdmin).getRole())
                .isEqualTo(Role.USER);
    }

    @Test
    void aCustomerCannotCreateAPartner() {
        assertThatThrownBy(() -> service.createDeliveryPartner(
                form("Rider", "r@x.in", "9000000003", "password-1"), customer))
                .isInstanceOf(AccessDeniedException.class);
    }
}
