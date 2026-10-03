package com.harish.pickles.web.mvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.MailDtos.MailFolder;
import com.harish.pickles.dto.MailDtos.MailMessageSummary;
import com.harish.pickles.dto.UserDtos.UserForm;
import com.harish.pickles.repository.UserRepository;
import com.harish.pickles.security.AppUserDetails;
import com.harish.pickles.service.MailInboxService;
import com.harish.pickles.service.UserAdminService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The super-admin-only "filter by user" control must never apply for a plain admin, even if the
 * userId query parameter is forged onto the request - there is one shared mailbox and only a
 * super admin may narrow its view to one user's messages.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mailtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMailViewControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private UserAdminService userAdminService;

    @MockBean private MailInboxService mailInboxService;

    private User admin;
    private User superAdmin;
    private User customer;

    @BeforeEach
    void setUp() {
        admin = users.findByEmailIgnoreCase("admin@srivinayakabakeryhome.com").orElseThrow();
        superAdmin = users.findByEmailIgnoreCase("superadmin@srivinayakabakeryhome.com").orElseThrow();
        String email = "mailtest-" + UUID.randomUUID().toString().substring(0, 8) + "@test.in";
        customer = userAdminService.createCustomer(
                new UserForm(null, "Mail Test Customer", email, "9876543210", "Password1", true), superAdmin);

        when(mailInboxService.list(eq(MailFolder.INBOX))).thenReturn(List.of(
                new MailMessageSummary(1L, customer.getEmail(), "admin@srivinayakabakeryhome.com",
                        "From the customer", Instant.now(), true),
                new MailMessageSummary(2L, "someone-else@test.in", "admin@srivinayakabakeryhome.com",
                        "From someone else", Instant.now(), true)));
    }

    @Test
    void plainAdminSeesTheWholeMailboxEvenWithAForgedUserIdParam() throws Exception {
        mvc.perform(get("/admin/mail").param("folder", "inbox").param("userId", customer.getId().toString())
                        .with(user(new AppUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("messages",
                        org.hamcrest.Matchers.iterableWithSize(2)));
    }

    @Test
    void superAdminFilterNarrowsToTheSelectedUsersMessages() throws Exception {
        mvc.perform(get("/admin/mail").param("folder", "inbox").param("userId", customer.getId().toString())
                        .with(user(new AppUserDetails(superAdmin))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("messages",
                        org.hamcrest.Matchers.iterableWithSize(1)));
    }
}
