package com.harish.pickles.web.mvc;

import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.UserDtos.UserForm;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.service.UserAdminService;
import com.harish.pickles.web.CurrentUser;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * People screens for the admin panel: customers, delivery partners and (super admin only)
 * admin accounts.
 *
 * <p>What each signed-in role may do to each account is decided in {@link UserAdminService};
 * the {@code canEditDetails} / {@code canCreate} flags here only hide controls the service
 * would refuse anyway.</p>
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountsViewController {

    private static final List<Role> CUSTOMER_ROLES = List.of(Role.USER);
    private static final List<Role> PARTNER_ROLES = List.of(Role.DELIVERY_PARTNER);
    private static final List<Role> MANAGER_ROLES = List.of(Role.STORE_MANAGER);
    private static final List<Role> STAFF_ROLES = List.of(Role.ADMIN, Role.SUPER_ADMIN);

    private final UserAdminService userAdminService;

    public AdminAccountsViewController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    // ------------------------------------------------------------------ lists

    @GetMapping("/customers")
    public String customers(@RequestParam(required = false) String q,
                            @RequestParam(defaultValue = "0") int page, Model model) {
        boolean superAdmin = CurrentUser.require().getRole() == Role.SUPER_ADMIN;
        return list(model, CUSTOMER_ROLES, q, page, "Customers", "customers", "/admin/customers",
                superAdmin ? "/admin/customers/new" : null, "Add a customer");
    }

    @GetMapping("/delivery-partners")
    public String deliveryPartners(@RequestParam(required = false) String q,
                                   @RequestParam(defaultValue = "0") int page, Model model) {
        return list(model, PARTNER_ROLES, q, page, "Delivery partners", "partners", "/admin/delivery-partners",
                "/admin/delivery-partners/new", "Add a delivery partner");
    }

    @GetMapping("/store-managers")
    public String storeManagers(@RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "0") int page, Model model) {
        return list(model, MANAGER_ROLES, q, page, "Store managers", "managers", "/admin/store-managers",
                "/admin/store-managers/new", "Add a store manager");
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String staff(@RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        return list(model, STAFF_ROLES, q, page, "Admin accounts", "staff", "/admin/staff", null, null);
    }

    private String list(Model model, List<Role> roles, String q, int page, String heading, String scope,
                        String basePath, String newPath, String newLabel) {
        model.addAttribute("users", userAdminService.list(roles, q, PageRequest.of(page, 20)));
        model.addAttribute("q", q);
        model.addAttribute("heading", heading);
        model.addAttribute("scope", scope);
        model.addAttribute("basePath", basePath);
        model.addAttribute("newPath", newPath);
        model.addAttribute("newLabel", newLabel);
        model.addAttribute("activePage", scope);
        return "admin/accounts";
    }

    // ----------------------------------------------------------------- create

    @GetMapping("/customers/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String newCustomer(Model model) {
        return createForm(model, "customer", "/admin/customers", "customers");
    }

    @GetMapping("/delivery-partners/new")
    public String newDeliveryPartner(Model model) {
        return createForm(model, "partner", "/admin/delivery-partners", "partners");
    }

    @GetMapping("/store-managers/new")
    public String newStoreManager(Model model) {
        return createForm(model, "manager", "/admin/store-managers", "managers");
    }

    /** Store managers run inventory and orders; configuration stays with admins. */
    @PostMapping("/store-managers")
    public String createStoreManager(@RequestParam String fullName,
                                     @RequestParam String email,
                                     @RequestParam(required = false) String phone,
                                     @RequestParam String password,
                                     @RequestParam(defaultValue = "false") boolean enabled,
                                     RedirectAttributes flash) {
        try {
            User created = userAdminService.createStoreManager(
                    new UserForm(null, fullName, email, phone, password, enabled), CurrentUser.require());
            flash.addFlashAttribute("message", created.getFullName()
                    + " can now sign in as a store manager with " + created.getEmail());
            return "redirect:/admin/store-managers";
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/store-managers/new";
        }
    }

    private String createForm(Model model, String kind, String basePath, String activePage) {
        model.addAttribute("account", null);
        model.addAttribute("kind", kind);
        model.addAttribute("basePath", basePath);
        model.addAttribute("suggestedPassword", UserAdminService.generatePassword());
        model.addAttribute("activePage", activePage);
        return "admin/account-form";
    }

    @PostMapping("/customers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String createCustomer(@RequestParam String fullName,
                                 @RequestParam String email,
                                 @RequestParam(required = false) String phone,
                                 @RequestParam String password,
                                 @RequestParam(defaultValue = "false") boolean enabled,
                                 RedirectAttributes flash) {
        try {
            User created = userAdminService.createCustomer(
                    new UserForm(null, fullName, email, phone, password, enabled), CurrentUser.require());
            flash.addFlashAttribute("message", created.getFullName() + " can now sign in with " + created.getEmail());
            return "redirect:/admin/customers";
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/customers/new";
        }
    }

    /** Add another partner any time - each one is its own account with its own sign-in. */
    @PostMapping("/delivery-partners")
    public String createDeliveryPartner(@RequestParam String fullName,
                                        @RequestParam String email,
                                        @RequestParam(required = false) String phone,
                                        @RequestParam String password,
                                        @RequestParam(defaultValue = "false") boolean enabled,
                                        RedirectAttributes flash) {
        try {
            User created = userAdminService.createDeliveryPartner(
                    new UserForm(null, fullName, email, phone, password, enabled), CurrentUser.require());
            flash.addFlashAttribute("message", created.getFullName()
                    + " can now sign in as a delivery partner with " + created.getEmail());
            return "redirect:/admin/delivery-partners";
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/delivery-partners/new";
        }
    }

    // ----------------------------------------------------------------- manage

    @GetMapping("/accounts/{id}/edit")
    public String editAccount(@PathVariable Long id, Model model) {
        User actor = CurrentUser.require();
        User account = userAdminService.getForManagement(id, actor);
        model.addAttribute("account", account);
        model.addAttribute("canEditDetails", UserAdminService.canEditDetails(actor, account));
        model.addAttribute("suggestedPassword", UserAdminService.generatePassword());
        model.addAttribute("basePath", listPathFor(account.getRole()));
        model.addAttribute("activePage", pageFor(account.getRole()));
        return "admin/account-form";
    }

    @PostMapping("/accounts/{id}")
    public String updateAccount(@PathVariable Long id,
                                @RequestParam String fullName,
                                @RequestParam String email,
                                @RequestParam(required = false) String phone,
                                @RequestParam(defaultValue = "false") boolean enabled,
                                RedirectAttributes flash) {
        try {
            User saved = userAdminService.updateDetails(id,
                    new UserForm(id, fullName, email, phone, null, enabled), CurrentUser.require());
            flash.addFlashAttribute("message", saved.getFullName() + "'s details were updated");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/accounts/" + id + "/edit";
    }

    @PostMapping("/accounts/{id}/password")
    public String resetPassword(@PathVariable Long id,
                                @RequestParam(required = false) String newPassword,
                                @RequestParam(defaultValue = "false") boolean generate,
                                RedirectAttributes flash) {
        try {
            User actor = CurrentUser.require();
            if (generate) {
                String created = userAdminService.resetPasswordToGenerated(id, actor);
                flash.addFlashAttribute("generatedPassword", created);
                flash.addFlashAttribute("message",
                        "New password generated. Copy it now - it is not shown again.");
            } else {
                User saved = userAdminService.resetPassword(id, newPassword, actor);
                flash.addFlashAttribute("message", "Password reset for " + saved.getEmail());
            }
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/accounts/" + id + "/edit";
    }

    @PostMapping("/accounts/{id}/enabled")
    public String setEnabled(@PathVariable Long id, @RequestParam boolean enabled, RedirectAttributes flash) {
        try {
            User saved = userAdminService.setEnabled(id, enabled, CurrentUser.require());
            flash.addFlashAttribute("message",
                    saved.getFullName() + (enabled ? " can sign in again" : " has been blocked from signing in"));
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/accounts/" + id + "/edit";
    }

    private static String pageFor(Role role) {
        return switch (role) {
            case USER -> "customers";
            case DELIVERY_PARTNER -> "partners";
            case STORE_MANAGER -> "managers";
            default -> "staff";
        };
    }

    private static String listPathFor(Role role) {
        return switch (role) {
            case USER -> "/admin/customers";
            case DELIVERY_PARTNER -> "/admin/delivery-partners";
            case STORE_MANAGER -> "/admin/store-managers";
            default -> "/admin/staff";
        };
    }
}
