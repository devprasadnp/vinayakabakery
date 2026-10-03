package com.harish.pickles.web.mvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.Product;
import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.OrderDtos.AddressRequest;
import com.harish.pickles.dto.OrderDtos.CheckoutRequest;
import com.harish.pickles.repository.ProductRepository;
import com.harish.pickles.repository.UserRepository;
import com.harish.pickles.security.AppUserDetails;
import com.harish.pickles.service.CartService;
import com.harish.pickles.service.OrderService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Renders every Thymeleaf page touched by the bakery re-theme and the Google Maps feature that
 * no other test happens to hit - a template typo (an unbalanced {@code th:if}, a bad OGNL
 * expression) throws at render time, not at compile time, so this is the only net that catches
 * one. {@code GOOGLE_MAPS_API_KEY} is blank in the test profile, which is deliberately the more
 * important path to cover: it is the default for anyone who has not set up a Maps key yet.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:viewsmoketest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ViewRenderingSmokeTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ProductRepository products;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;

    @Test
    void publicStorefrontPagesRender() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/products")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
    }

    @Test
    void checkoutAndOrderDetailRenderForASignedInCustomer() throws Exception {
        User buyer = newUser();
        Product product = products.findAll().stream().filter(p -> p.getStockQty() > 5).findFirst().orElseThrow();
        cartService.increment(buyer, product.getId(), 1);

        mvc.perform(get("/checkout").with(user(new AppUserDetails(buyer)))).andExpect(status().isOk());

        Order order = orderService.placeOrder(buyer, checkout());
        mvc.perform(get("/orders/" + order.getOrderNumber()).with(user(new AppUserDetails(buyer))))
                .andExpect(status().isOk());
    }

    @Test
    void adminDashboardAndStoreLocationRenderForAnAdmin() throws Exception {
        User admin = users.findByEmailIgnoreCase("admin@srivinayakabakeryhome.com").orElseThrow();
        mvc.perform(get("/admin").with(user(new AppUserDetails(admin)))).andExpect(status().isOk());
        mvc.perform(get("/admin/store-location").with(user(new AppUserDetails(admin)))).andExpect(status().isOk());
    }

    private User newUser() {
        String email = "smoke-" + UUID.randomUUID().toString().substring(0, 8) + "@test.in";
        User u = new User("Smoke Test", email, encoder.encode("Password1"), Role.USER);
        u.setPhone("9876543210");
        return users.save(u);
    }

    private static CheckoutRequest checkout() {
        return new CheckoutRequest(new AddressRequest("Buyer", "buyer@test.in", "9876543210",
                "1-2-3 Main Road", null, "Kakinada", "Andhra Pradesh", "533001"), null);
    }
}
