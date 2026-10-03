package com.harish.pickles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.harish.pickles.domain.DeliveryPincode;
import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.OrderStatus;
import com.harish.pickles.domain.Product;
import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.OrderDtos.AddressRequest;
import com.harish.pickles.dto.OrderDtos.CheckoutRequest;
import com.harish.pickles.dto.UserDtos.UserForm;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.exception.NotFoundException;
import com.harish.pickles.repository.DeliveryPincodeRepository;
import com.harish.pickles.repository.OrderRepository;
import com.harish.pickles.repository.PasswordResetTokenRepository;
import com.harish.pickles.repository.ProductRepository;
import com.harish.pickles.repository.UserRepository;
import com.harish.pickles.security.AppUserDetails;
import com.harish.pickles.service.CartService;
import com.harish.pickles.service.DeliveryZoneService;
import com.harish.pickles.service.MailService;
import com.harish.pickles.service.OrderService;
import com.harish.pickles.service.PaymentService;
import com.harish.pickles.service.PasswordResetService;
import com.harish.pickles.service.UserAdminService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The three features end to end, through the real Spring Security filter chain: delivery-area
 * restriction at checkout, the delivery-partner role, and email password reset. Uses its own
 * in-memory database so it cannot disturb the other Spring tests.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:flowtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeliveryAndPasswordFlowTest {

    private static final String PASSWORD = "OldPassword1";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PasswordResetTokenRepository tokens;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orders;
    @Autowired private CartService cartService;
    @Autowired private ProductRepository products;
    @Autowired private DeliveryZoneService zones;
    @Autowired private DeliveryPincodeRepository pincodeRepository;
    @Autowired private UserAdminService userAdminService;
    @Autowired private PaymentService paymentService;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private MailService mail;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = users.findByEmailIgnoreCase("admin@srivinayakabakeryhome.com").orElseThrow();
    }

    @AfterEach
    void switchRestrictionOff() {
        zones.setRestricted(false);
        pincodeRepository.deleteAll();
    }

    // ------------------------------------------------------------------ helpers

    private User newUser(Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.in";
        User u = new User("Test " + role.getLabel(), email, encoder.encode(PASSWORD), role);
        u.setPhone("9876543210");
        return users.save(u);
    }

    private User newPartner() {
        String email = "rider-" + UUID.randomUUID().toString().substring(0, 8) + "@test.in";
        return userAdminService.createDeliveryPartner(
                new UserForm(null, "Rider", email, "9123456780", PASSWORD, true), admin);
    }

    private static CheckoutRequest checkout(String pincode) {
        return new CheckoutRequest(new AddressRequest("Buyer", "buyer@test.in", "9876543210",
                "1-2-3 Main Road", null, "Kakinada", "Andhra Pradesh", pincode), null);
    }

    private Product stockedProduct() {
        return products.findAll().stream().filter(p -> p.getStockQty() > 5).findFirst().orElseThrow();
    }

    private String resetTokenFromMail(String email) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mail, timeout(2000)).sendPasswordReset(eq(email), any(), link.capture(), anyLong());
        return link.getValue().substring(link.getValue().indexOf("token=") + "token=".length());
    }

    // ================================================================ password reset

    @Test
    void forgotPasswordEmailsALinkThatChangesThePasswordExactlyOnce() throws Exception {
        User u = newUser(Role.USER);

        mvc.perform(post("/forgot-password").param("email", u.getEmail()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("message"));
        String token = resetTokenFromMail(u.getEmail());

        // The form opens for a good token, and tells the browser not to leak it via Referer.
        mvc.perform(get("/reset-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Choose a new password")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        // Mismatched confirmation: rejected, and the link is still good.
        mvc.perform(post("/reset-password").param("token", token)
                        .param("password", "NewPassword2").param("confirmPassword", "different").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("do not match")));
        assertThat(passwordResetService.isValid(token)).isTrue();

        // Too short: rejected.
        mvc.perform(post("/reset-password").param("token", token)
                        .param("password", "short").param("confirmPassword", "short").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("at least 8")));

        // Good change.
        mvc.perform(post("/reset-password").param("token", token)
                        .param("password", "NewPassword2").param("confirmPassword", "NewPassword2").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mvc.perform(formLogin("/login").user("email", u.getEmail()).password("password", "NewPassword2"))
                .andExpect(authenticated());
        mvc.perform(formLogin("/login").user("email", u.getEmail()).password("password", PASSWORD))
                .andExpect(unauthenticated());

        // The link is spent.
        assertThat(passwordResetService.isValid(token)).isFalse();
        mvc.perform(post("/reset-password").param("token", token)
                        .param("password", "ThirdPassword3").param("confirmPassword", "ThirdPassword3").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("invalid or has expired")));
        mvc.perform(formLogin("/login").user("email", u.getEmail()).password("password", "ThirdPassword3"))
                .andExpect(unauthenticated());
    }

    @Test
    void unknownEmailGetsTheSameAnswerAndNothingIsSent() throws Exception {
        mvc.perform(post("/forgot-password").param("email", "nobody-" + UUID.randomUUID() + "@test.in").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("message"));

        verifyNoInteractions(mail);
    }

    @Test
    void blockedAccountsGetNoResetEmail() throws Exception {
        User u = newUser(Role.USER);
        u.setEnabled(false);
        users.save(u);

        mvc.perform(post("/forgot-password").param("email", u.getEmail()).with(csrf()))
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(mail);
    }

    @Test
    void aSecondRequestInsideTheCooldownSendsNothingMoreButAfterItANewLinkReplacesTheOld() {
        User u = newUser(Role.USER);

        passwordResetService.requestReset(u.getEmail());
        passwordResetService.requestReset(u.getEmail());
        String first = resetTokenFromMail(u.getEmail()); // exactly one mail so far
        verify(mail, times(1)).sendPasswordReset(eq(u.getEmail()), any(), any(), anyLong());

        // Age the first link past the cooldown, then ask again.
        jdbc.update("UPDATE password_reset_tokens SET created_at = ? WHERE user_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(5)), u.getId());
        passwordResetService.requestReset(u.getEmail());

        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);
        verify(mail, times(2)).sendPasswordReset(eq(u.getEmail()), any(), links.capture(), anyLong());
        String second = links.getAllValues().get(1).substring(links.getAllValues().get(1).indexOf("token=") + 6);

        assertThat(second).isNotEqualTo(first);
        assertThat(passwordResetService.isValid(first)).as("older link is dead").isFalse();
        assertThat(passwordResetService.isValid(second)).isTrue();
    }

    @Test
    void anExpiredLinkIsRejected() {
        User u = newUser(Role.USER);
        passwordResetService.requestReset(u.getEmail());
        String token = resetTokenFromMail(u.getEmail());
        assertThat(passwordResetService.isValid(token)).isTrue();

        jdbc.update("UPDATE password_reset_tokens SET expires_at = ? WHERE user_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), u.getId());

        assertThat(passwordResetService.isValid(token)).isFalse();
        assertThatThrownBy(() -> passwordResetService.completeReset(token, "NewPassword2"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("invalid or has expired");
    }

    @Test
    void theDatabaseNeverHoldsTheUsableToken() {
        User u = newUser(Role.USER);
        passwordResetService.requestReset(u.getEmail());
        String token = resetTokenFromMail(u.getEmail());

        String stored = jdbc.queryForObject(
                "SELECT token_hash FROM password_reset_tokens WHERE user_id = ?", String.class, u.getId());

        assertThat(stored).isNotEqualTo(token).hasSize(64);
        assertThat(passwordResetService.isValid(stored)).as("the hash itself is not a working token").isFalse();
    }

    @Test
    void garbageTokensAreHarmless() throws Exception {
        for (String bad : new String[] {"", "nope", "a".repeat(200)}) {
            assertThat(passwordResetService.isValid(bad)).isFalse();
        }
        mvc.perform(get("/reset-password").param("token", "nope"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("can't be used")));
        mvc.perform(get("/reset-password")).andExpect(status().isOk());
    }

    @Test
    void theApiVersionOfTheFlowWorksToo() throws Exception {
        User u = newUser(Role.USER);

        mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                        .content("{\"email\":\"" + u.getEmail() + "\"}"))
                .andExpect(status().isAccepted());
        String token = resetTokenFromMail(u.getEmail());

        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"ApiPassword9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reset"));
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"ApiPassword9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    // ============================================================ delivery restriction

    @Test
    void anAddressOutsideTheListIsTurnedAwayBeforeAnyOrderOrPaymentExists() throws Exception {
        User buyer = newUser(Role.USER);
        Product product = stockedProduct();
        int stockBefore = product.getStockQty();
        cartService.increment(buyer, product.getId(), 1);
        pincodeRepository.save(new DeliveryPincode("533001", "Kakinada"));
        zones.setRestricted(true);

        // Straight through the checkout form: bounced back to /checkout with the message - never to a pay page.
        mvc.perform(post("/checkout").with(user(new AppUserDetails(buyer))).with(csrf())
                        .param("contactName", "Buyer").param("contactEmail", "buyer@test.in")
                        .param("contactPhone", "9876543210").param("addressLine1", "1-2-3 Main Road")
                        .param("city", "Bengaluru").param("state", "Karnataka").param("pincode", "560001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout"))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("don't have a delivery partner"),
                        org.hamcrest.Matchers.containsString("560001"))));

        assertThat(orders.findByUserOrderByIdDesc(buyer)).as("no order was created").isEmpty();
        assertThat(products.findById(product.getId()).orElseThrow().getStockQty())
                .as("no stock was reserved").isEqualTo(stockBefore);
        assertThat(cartService.items(buyer)).as("the cart is untouched").hasSize(1);
    }

    @Test
    void anAddressInsideTheListGoesThrough() throws Exception {
        User buyer = newUser(Role.USER);
        cartService.increment(buyer, stockedProduct().getId(), 1);
        pincodeRepository.save(new DeliveryPincode("533001", "Kakinada"));
        zones.setRestricted(true);

        mvc.perform(post("/checkout").with(user(new AppUserDetails(buyer))).with(csrf())
                        .param("contactName", "Buyer").param("contactEmail", "buyer@test.in")
                        .param("contactPhone", "9876543210").param("addressLine1", "1-2-3 Main Road")
                        .param("city", "Kakinada").param("state", "Andhra Pradesh").param("pincode", " 533001 "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*/pay-qr"));

        Order placed = orders.findByUserOrderByIdDesc(buyer).get(0);
        assertThat(placed.getShippingAddress().getPincode()).as("stored trimmed").isEqualTo("533001");
    }

    @Test
    void theRestApiEnforcesTheSameRule() {
        User buyer = newUser(Role.USER);
        cartService.increment(buyer, stockedProduct().getId(), 1);
        pincodeRepository.save(new DeliveryPincode("533001", null));
        zones.setRestricted(true);

        assertThatThrownBy(() -> orderService.placeOrder(buyer, checkout("110001")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("110001");
        assertThat(orderService.placeOrder(buyer, checkout("533001")).getOrderNumber()).startsWith("HP-");
    }

    @Test
    void withTheSwitchOffAnyAddressStillWorksExactlyAsBefore() {
        User buyer = newUser(Role.USER);
        cartService.increment(buyer, stockedProduct().getId(), 1);
        assertThat(zones.isRestricted()).isFalse();

        assertThat(orderService.placeOrder(buyer, checkout("110001")).getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void theCheckoutPageCanAskAnonymouslyWhetherWeDeliver() throws Exception {
        pincodeRepository.save(new DeliveryPincode("533001", "Kakinada"));
        zones.setRestricted(true);

        mvc.perform(get("/api/delivery-areas/check").param("pincode", "533001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverable").value(true))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Kakinada")));
        mvc.perform(get("/api/delivery-areas/check").param("pincode", "560001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverable").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("delivery partner")));
        mvc.perform(get("/api/delivery-areas/check").param("pincode", "12"))
                .andExpect(jsonPath("$.deliverable").value(false));
    }

    @Test
    void adminAndSuperAdminCanManageTheListButACustomerCannot() throws Exception {
        User superAdmin = users.findByEmailIgnoreCase("superadmin@srivinayakabakeryhome.com").orElseThrow();
        for (User actor : new User[] {admin, superAdmin}) {
            mvc.perform(post("/admin/delivery-areas").with(user(new AppUserDetails(actor))).with(csrf())
                            .param("pincodes", "533010, 533011").param("areaName", "Test area"))
                    .andExpect(redirectedUrl("/admin/delivery-areas"));
        }
        assertThat(pincodeRepository.findByPincode("533010")).isPresent();
        assertThat(pincodeRepository.findByPincode("533011")).isPresent();

        mvc.perform(get("/admin/delivery-areas").with(user(new AppUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("533010")));

        User customer = newUser(Role.USER);
        mvc.perform(post("/admin/delivery-areas").with(user(new AppUserDetails(customer))).with(csrf())
                        .param("pincodes", "533099"))
                .andExpect(status().isForbidden());
        assertThat(pincodeRepository.findByPincode("533099")).isEmpty();
    }

    // ============================================================= delivery partner

    @Test
    void aDeliveryPartnerSignsInToTheirOwnScreenAndNowhereElse() throws Exception {
        User partner = newPartner();
        User customer = newUser(Role.USER);

        mvc.perform(formLogin("/login").user("email", partner.getEmail()).password("password", PASSWORD))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/delivery"));
        mvc.perform(formLogin("/login").user("email", customer.getEmail()).password("password", PASSWORD))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/delivery").with(user(new AppUserDetails(partner)))).andExpect(status().isOk());
        mvc.perform(get("/admin").with(user(new AppUserDetails(partner)))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/orders").with(user(new AppUserDetails(partner)))).andExpect(status().isForbidden());
        mvc.perform(get("/superadmin/archive").with(user(new AppUserDetails(partner)))).andExpect(status().isForbidden());
        mvc.perform(get("/delivery").with(user(new AppUserDetails(customer)))).andExpect(status().isForbidden());
        mvc.perform(get("/delivery")).andExpect(status().is3xxRedirection());
    }

    @Test
    void anAdminCanCreateManyPartnersAndTheyCanSignInWithTheirOwnCredentials() throws Exception {
        for (int i = 1; i <= 3; i++) {
            String email = "multi" + i + "-" + UUID.randomUUID().toString().substring(0, 6) + "@test.in";
            mvc.perform(post("/admin/delivery-partners").with(user(new AppUserDetails(admin))).with(csrf())
                            .param("fullName", "Rider " + i).param("email", email)
                            .param("phone", "912345678" + i).param("password", "Password" + i + "xyz")
                            .param("enabled", "true"))
                    .andExpect(redirectedUrl("/admin/delivery-partners"));

            assertThat(users.findByEmailIgnoreCase(email).orElseThrow().getRole()).isEqualTo(Role.DELIVERY_PARTNER);
            mvc.perform(formLogin("/login").user("email", email).password("password", "Password" + i + "xyz"))
                    .andExpect(authenticated()).andExpect(redirectedUrl("/delivery"));
        }
        assertThat(users.countByRole(Role.DELIVERY_PARTNER)).isGreaterThanOrEqualTo(3);
    }

    @Test
    void anOrderMovesFromAssignedToDeliveredOnlyInTheAllowedSteps() throws Exception {
        User buyer = newUser(Role.USER);
        User partner = newPartner();
        User otherPartner = newPartner();
        cartService.increment(buyer, stockedProduct().getId(), 1);
        String number = orderService.placeOrder(buyer, checkout("533001")).getOrderNumber();

        // Not paid yet, so it cannot be handed over.
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(number, partner.getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("paid order");
        orderService.updateStatus(number, OrderStatus.PAID);

        // Only genuine, enabled delivery partners can be chosen.
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(number, buyer.getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("active delivery partner");
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(number, admin.getId()))
                .isInstanceOf(BusinessException.class);
        orderService.assignDeliveryPartner(number, partner.getId());

        // The assigned partner sees it; the other one does not, and cannot touch it.
        mvc.perform(get("/delivery").with(user(new AppUserDetails(partner))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(number)));
        mvc.perform(get("/delivery").with(user(new AppUserDetails(otherPartner))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(number))));
        assertThatThrownBy(() -> orderService.updateStatusAsPartner(number, otherPartner, OrderStatus.SHIPPED))
                .isInstanceOf(NotFoundException.class);

        // Cannot skip straight to delivered, cannot cancel or refund.
        assertThatThrownBy(() -> orderService.updateStatusAsPartner(number, partner, OrderStatus.DELIVERED))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> orderService.updateStatusAsPartner(number, partner, OrderStatus.CANCELLED))
                .isInstanceOf(BusinessException.class);

        mvc.perform(post("/delivery/orders/" + number + "/status").with(user(new AppUserDetails(partner))).with(csrf())
                        .param("status", "SHIPPED"))
                .andExpect(redirectedUrl("/delivery"));
        assertThat(orderService.getByOrderNumber(number).getStatus()).isEqualTo(OrderStatus.SHIPPED);

        mvc.perform(post("/delivery/orders/" + number + "/status").with(user(new AppUserDetails(partner))).with(csrf())
                        .param("status", "DELIVERED"))
                .andExpect(redirectedUrl("/delivery"));
        assertThat(orderService.getByOrderNumber(number).getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // Finished orders cannot be reassigned.
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(number, otherPartner.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void aPartnerCannotSeeOrdersThatAreNotTheirs() {
        User buyer = newUser(Role.USER);
        User partner = newPartner();
        cartService.increment(buyer, stockedProduct().getId(), 1);
        String number = orderService.placeOrder(buyer, checkout("533001")).getOrderNumber();

        assertThatThrownBy(() -> orderService.getForUser(number, partner)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void adminAssignsFromTheOrdersScreenAndCanTakeTheOrderBack() throws Exception {
        User buyer = newUser(Role.USER);
        User partner = newPartner();
        cartService.increment(buyer, stockedProduct().getId(), 1);
        String number = orderService.placeOrder(buyer, checkout("533001")).getOrderNumber();
        orderService.updateStatus(number, OrderStatus.PAID);

        mvc.perform(post("/admin/orders/" + number + "/assign").with(user(new AppUserDetails(admin))).with(csrf())
                        .param("partnerId", partner.getId().toString()))
                .andExpect(redirectedUrl("/admin/orders"))
                .andExpect(flash().attribute("message", org.hamcrest.Matchers.containsString(partner.getFullName())));
        mvc.perform(get("/admin/orders").with(user(new AppUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("With " + partner.getFullName())));

        mvc.perform(post("/admin/orders/" + number + "/assign").with(user(new AppUserDetails(admin))).with(csrf())
                        .param("partnerId", ""))
                .andExpect(redirectedUrl("/admin/orders"));
        assertThat(orders.findByOrderNumber(number).orElseThrow().getDeliveryPartner()).isNull();
    }

    @Test
    void aPartnerCannotUseTheAdminOrCustomerApis() throws Exception {
        User partner = newPartner();
        String jwt = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + partner.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DELIVERY_PARTNER"))
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(jwt, "$.token");

        mvc.perform(get("/api/delivery/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/delivery-partners").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ================================================================ admin resets

    @Test
    void adminCanResetACustomersPasswordButNotTheSuperAdmins() throws Exception {
        User customer = newUser(Role.USER);
        User superAdmin = users.findByEmailIgnoreCase("superadmin@srivinayakabakeryhome.com").orElseThrow();
        String superHashBefore = superAdmin.getPasswordHash();

        mvc.perform(post("/admin/accounts/" + customer.getId() + "/password")
                        .with(user(new AppUserDetails(admin))).with(csrf())
                        .param("newPassword", "ResetByAdmin7"))
                .andExpect(redirectedUrl("/admin/accounts/" + customer.getId() + "/edit"))
                .andExpect(flash().attributeExists("message"));
        mvc.perform(formLogin("/login").user("email", customer.getEmail()).password("password", "ResetByAdmin7"))
                .andExpect(authenticated());

        mvc.perform(post("/admin/accounts/" + superAdmin.getId() + "/password")
                        .with(user(new AppUserDetails(admin))).with(csrf())
                        .param("newPassword", "TakeOverAttempt1"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/accounts/" + superAdmin.getId() + "/edit").with(user(new AppUserDetails(admin))))
                .andExpect(status().isForbidden());
        assertThat(users.findById(superAdmin.getId()).orElseThrow().getPasswordHash()).isEqualTo(superHashBefore);
    }

    @Test
    void adminCanNotSeeTheAdminAccountsListButSuperAdminCan() throws Exception {
        User superAdmin = users.findByEmailIgnoreCase("superadmin@srivinayakabakeryhome.com").orElseThrow();

        mvc.perform(get("/admin/staff").with(user(new AppUserDetails(admin)))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/staff").with(user(new AppUserDetails(superAdmin)))).andExpect(status().isOk());
        mvc.perform(get("/admin/customers").with(user(new AppUserDetails(admin)))).andExpect(status().isOk());
        mvc.perform(get("/superadmin/customers").with(user(new AppUserDetails(superAdmin))))
                .andExpect(redirectedUrl("/admin/customers"));
    }

    // ================================================= pending orders and the delivery area

    /** An order as the QR flow leaves it: placed, stock reserved, waiting for the customer to pay. */
    private String pendingOrder(User buyer, String pincode) {
        cartService.increment(buyer, stockedProduct().getId(), 1);
        String number = orderService.placeOrder(buyer, checkout(pincode)).getOrderNumber();
        orderService.updateStatus(number, OrderStatus.PENDING_PAYMENT);
        return number;
    }

    private OrderStatus statusOf(String orderNumber) {
        return orders.findByOrderNumber(orderNumber).orElseThrow().getStatus();
    }

    private int stockOf(Product product) {
        return products.findById(product.getId()).orElseThrow().getStockQty();
    }

    private void serve(String... pincodes) {
        for (String pincode : pincodes) {
            pincodeRepository.save(new DeliveryPincode(pincode, null));
        }
        zones.setRestricted(true);
    }

    private void stopServing(String pincode) {
        zones.remove(pincodeRepository.findByPincode(pincode).orElseThrow().getId());
    }

    @Test
    void clickingToPayOnAnOrderInAnAreaWeStoppedServingCancelsItAndReturnsTheStock() throws Exception {
        User buyer = newUser(Role.USER);
        Product product = stockedProduct();
        int stockBefore = stockOf(product);
        serve("533001", "533002");
        cartService.increment(buyer, product.getId(), 1);
        String number = orderService.placeOrder(buyer, checkout("533001")).getOrderNumber();
        orderService.updateStatus(number, OrderStatus.PENDING_PAYMENT);
        assertThat(stockOf(product)).as("stock is reserved while the order waits").isEqualTo(stockBefore - 1);

        stopServing("533001"); // the admin drops the area while the order is still unpaid

        mvc.perform(get("/orders/" + number + "/pay-qr").with(user(new AppUserDetails(buyer))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + number))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("cancelled"),
                        org.hamcrest.Matchers.containsString("533001"),
                        org.hamcrest.Matchers.containsString("delivery partner"))));

        assertThat(statusOf(number)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(product)).as("stock went back on the shelf").isEqualTo(stockBefore);

        // Going back again does not show a QR, and does not hand the stock back a second time.
        mvc.perform(get("/orders/" + number + "/pay-qr").with(user(new AppUserDetails(buyer))))
                .andExpect(redirectedUrl("/orders/" + number))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("cannot be paid")));
        assertThat(stockOf(product)).isEqualTo(stockBefore);

        // The QR image is refused, and an admin cannot mark the cancelled order paid.
        mvc.perform(get("/orders/" + number + "/qr-code.png").with(user(new AppUserDetails(buyer))))
                .andExpect(status().isConflict());
        long total = orderService.getByOrderNumber(number).getTotalPaise();
        assertThatThrownBy(() -> paymentService.confirmManualPayment(number, "UTR-CANCELLED", total))
                .isInstanceOf(BusinessException.class).hasMessageContaining("cannot be paid");
    }

    @Test
    void anOrderInAServedAreaStillGetsItsQrAndStaysPending() throws Exception {
        User buyer = newUser(Role.USER);
        serve("533001", "533002");
        String number = pendingOrder(buyer, "533001");

        mvc.perform(get("/orders/" + number + "/pay-qr").with(user(new AppUserDetails(buyer))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(number)));
        mvc.perform(get("/orders/" + number + "/qr-code.png").with(user(new AppUserDetails(buyer))))
                .andExpect(status().isOk());

        assertThat(statusOf(number)).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void switchingTheRestrictionOnAlsoCatchesOrdersPlacedBeforeItExisted() throws Exception {
        User buyer = newUser(Role.USER);
        String number = pendingOrder(buyer, "560001"); // no restriction yet, so this was fine when placed
        assertThat(statusOf(number)).isEqualTo(OrderStatus.PENDING_PAYMENT);

        serve("533001");

        mvc.perform(get("/orders/" + number + "/pay-qr").with(user(new AppUserDetails(buyer))))
                .andExpect(redirectedUrl("/orders/" + number))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("560001")));
        assertThat(statusOf(number)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void withNoRestrictionPendingOrdersAreNeverTouched() throws Exception {
        User buyer = newUser(Role.USER);
        String number = pendingOrder(buyer, "560001");
        assertThat(zones.isRestricted()).isFalse();

        mvc.perform(get("/orders/" + number + "/pay-qr").with(user(new AppUserDetails(buyer))))
                .andExpect(status().isOk());
        assertThat(statusOf(number)).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void retryingPaymentIsAlsoRefusedAndCancelsTheOrder() throws Exception {
        User buyer = newUser(Role.USER);
        serve("533001", "533002");
        String number = pendingOrder(buyer, "533001");
        stopServing("533001");

        mvc.perform(post("/orders/" + number + "/retry-payment").with(user(new AppUserDetails(buyer))).with(csrf()))
                .andExpect(redirectedUrl("/orders/" + number))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("cancelled")));
        assertThat(statusOf(number)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void theApiPayEndpointCancelsAndRefusesToo() throws Exception {
        User buyer = newUser(Role.USER);
        serve("533001", "533002");
        String number = pendingOrder(buyer, "533001");
        stopServing("533001");
        String login = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + buyer.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(login, "$.token");

        mvc.perform(post("/api/orders/" + number + "/pay").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cancelled")));

        assertThat(statusOf(number)).as("the cancellation is committed, not rolled back with the error")
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void anOrderThatIsAlreadyPaidIsNeverCancelledByTheAreaRule() throws Exception {
        User buyer = newUser(Role.USER);
        String number = pendingOrder(buyer, "560001");
        orderService.updateStatus(number, OrderStatus.PAID);
        serve("533001"); // 560001 is now outside the area

        assertThat(orderService.cancelIfOutsideDeliveryArea(number)).isFalse();
        mvc.perform(get("/orders/" + number + "/pay-qr").with(user(new AppUserDetails(buyer))))
                .andExpect(redirectedUrl("/orders/" + number))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("cannot be paid")));
        assertThat(statusOf(number)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void anAdminCannotConfirmPaymentForAnOrderWeCanNoLongerDeliverEvenBeforeItIsCancelled() {
        User buyer = newUser(Role.USER);
        serve("533001", "533002");
        String number = pendingOrder(buyer, "533001");
        stopServing("533001");
        long total = orderService.getByOrderNumber(number).getTotalPaise();

        assertThatThrownBy(() -> paymentService.confirmManualPayment(number, "UTR-OUT-OF-AREA", total))
                .isInstanceOf(BusinessException.class).hasMessageContaining("outside the delivery area");
        assertThat(statusOf(number)).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void anOrderWhosePaymentAlreadyFailedDoesNotGetItsStockReturnedTwice() {
        User buyer = newUser(Role.USER);
        Product product = stockedProduct();
        int stockBefore = stockOf(product);
        serve("533001", "533002");
        String number = pendingOrder(buyer, "533001");

        // A failed payment hands the stock back and marks the order PAYMENT_FAILED (see PaymentService.apply).
        jdbc.update("UPDATE products SET stock_qty = stock_qty + 1 WHERE id = ?", product.getId());
        jdbc.update("UPDATE orders SET status = 'PAYMENT_FAILED' WHERE order_number = ?", number);
        assertThat(stockOf(product)).isEqualTo(stockBefore);

        stopServing("533001");

        assertThat(orderService.cancelIfOutsideDeliveryArea(number)).isTrue();
        assertThat(statusOf(number)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(product)).as("no phantom stock created").isEqualTo(stockBefore);
    }
}
