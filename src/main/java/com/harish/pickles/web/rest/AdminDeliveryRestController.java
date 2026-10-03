package com.harish.pickles.web.rest;

import com.harish.pickles.domain.Role;
import com.harish.pickles.dto.DeliveryDtos.AddPincodesRequest;
import com.harish.pickles.dto.DeliveryDtos.DeliverySettings;
import com.harish.pickles.dto.DeliveryDtos.PincodeResponse;
import com.harish.pickles.dto.OrderDtos.OrderResponse;
import com.harish.pickles.dto.UserDtos.UserForm;
import com.harish.pickles.dto.UserDtos.UserResponse;
import com.harish.pickles.service.DeliveryZoneService;
import com.harish.pickles.service.DeliveryZoneService.BulkResult;
import com.harish.pickles.service.OrderService;
import com.harish.pickles.service.UserAdminService;
import com.harish.pickles.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin and super admin: where we deliver, who delivers, and password resets. Who may touch which
 * account is enforced in {@link UserAdminService}, not here.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Delivery & accounts (admin)")
public class AdminDeliveryRestController {

    private final DeliveryZoneService deliveryZoneService;
    private final UserAdminService userAdminService;
    private final OrderService orderService;

    public AdminDeliveryRestController(DeliveryZoneService deliveryZoneService, UserAdminService userAdminService,
                                       OrderService orderService) {
        this.deliveryZoneService = deliveryZoneService;
        this.userAdminService = userAdminService;
        this.orderService = orderService;
    }

    // ---------------------------------------------------------- delivery areas

    @GetMapping("/delivery-areas")
    @Operation(summary = "PIN codes we deliver to")
    public Page<PincodeResponse> pincodes(@RequestParam(required = false) String q,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return deliveryZoneService.list(q, PageRequest.of(page, Math.min(size, 200))).map(PincodeResponse::from);
    }

    @GetMapping("/delivery-areas/settings")
    @Operation(summary = "Is the delivery restriction on?")
    public DeliverySettings settings() {
        return new DeliverySettings(deliveryZoneService.isRestricted(), deliveryZoneService.activeCount());
    }

    @PutMapping("/delivery-areas/settings")
    @Operation(summary = "Switch the restriction on or off. While on, orders outside the listed PIN codes are refused.")
    public DeliverySettings setRestricted(@RequestParam boolean restricted) {
        deliveryZoneService.setRestricted(restricted);
        return settings();
    }

    @PostMapping("/delivery-areas")
    @Operation(summary = "Add one or many PIN codes (separated by commas, spaces or new lines)")
    public ResponseEntity<BulkResult> addPincodes(@RequestBody AddPincodesRequest request) {
        return ResponseEntity.status(201).body(deliveryZoneService.addAll(request.pincodes(), request.areaName()));
    }

    @PatchMapping("/delivery-areas/{id}/active")
    @Operation(summary = "Pause or resume deliveries to one PIN code")
    public PincodeResponse setPincodeActive(@PathVariable Long id, @RequestParam boolean active) {
        return PincodeResponse.from(deliveryZoneService.setActive(id, active));
    }

    @DeleteMapping("/delivery-areas/{id}")
    @Operation(summary = "Remove a PIN code")
    public Map<String, String> removePincode(@PathVariable Long id) {
        return Map.of("deleted", deliveryZoneService.remove(id));
    }

    // ------------------------------------------------------- delivery partners

    @GetMapping("/delivery-partners")
    @Operation(summary = "Search delivery partners")
    public Page<UserResponse> partners(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return userAdminService.list(List.of(Role.DELIVERY_PARTNER), q, PageRequest.of(page, Math.min(size, 100)))
                .map(UserResponse::from);
    }

    @PostMapping("/delivery-partners")
    @Operation(summary = "Add a delivery partner (call again to add more)")
    public ResponseEntity<UserResponse> createPartner(@RequestBody UserForm form) {
        return ResponseEntity.status(201)
                .body(UserResponse.from(userAdminService.createDeliveryPartner(form, CurrentUser.require())));
    }

    @PutMapping("/accounts/{id}")
    @Operation(summary = "Edit a delivery partner's details (never their role)")
    public UserResponse updateAccount(@PathVariable Long id, @RequestBody UserForm form) {
        return UserResponse.from(userAdminService.updateDetails(id, form, CurrentUser.require()));
    }

    @PatchMapping("/accounts/{id}/enabled")
    @Operation(summary = "Block or unblock a delivery partner's sign-in")
    public UserResponse setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return UserResponse.from(userAdminService.setEnabled(id, enabled, CurrentUser.require()));
    }

    // ----------------------------------------------------- customers & passwords

    @GetMapping("/customers")
    @Operation(summary = "Search customer accounts")
    public Page<UserResponse> customers(@RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return userAdminService.list(List.of(Role.USER), q, PageRequest.of(page, Math.min(size, 100)))
                .map(UserResponse::from);
    }

    @PostMapping("/accounts/{id}/password")
    @Operation(summary = "Reset a customer's or delivery partner's password. Omit newPassword to have one generated and returned once.")
    public Map<String, String> resetPassword(@PathVariable Long id,
                                             @RequestParam(required = false) String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            return Map.of("generatedPassword", userAdminService.resetPasswordToGenerated(id, CurrentUser.require()));
        }
        userAdminService.resetPassword(id, newPassword, CurrentUser.require());
        return Map.of("status", "reset");
    }

    // ------------------------------------------------------------- assignment

    @PutMapping("/orders/{orderNumber}/delivery-partner")
    @Operation(summary = "Hand an order to a delivery partner. Omit partnerId to take it back.")
    public OrderResponse assign(@PathVariable String orderNumber, @RequestParam(required = false) Long partnerId) {
        return OrderResponse.from(orderService.assignDeliveryPartner(orderNumber, partnerId));
    }
}
