package com.harish.pickles.web.rest;

import com.harish.pickles.domain.Role;
import com.harish.pickles.dto.CategoryDtos.CategoryResponse;
import com.harish.pickles.dto.CouponDtos.CouponResponse;
import com.harish.pickles.dto.OrderDtos.OrderResponse;
import com.harish.pickles.dto.ProductDtos.ProductResponse;
import com.harish.pickles.dto.UserDtos.UserForm;
import com.harish.pickles.dto.UserDtos.UserResponse;
import com.harish.pickles.service.CouponService;
import com.harish.pickles.service.InventoryCategoryService;
import com.harish.pickles.service.OrderService;
import com.harish.pickles.service.ProductService;
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

@RestController
@RequestMapping("/api/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super admin")
public class SuperAdminRestController {

    private static final List<Role> CUSTOMER_ROLES = List.of(Role.USER);
    private static final List<Role> STAFF_ROLES = List.of(Role.ADMIN, Role.SUPER_ADMIN);

    private final UserAdminService userAdminService;
    private final ProductService productService;
    private final OrderService orderService;
    private final CouponService couponService;
    private final InventoryCategoryService categoryService;

    public SuperAdminRestController(UserAdminService userAdminService, ProductService productService,
                                    OrderService orderService, CouponService couponService,
                                    InventoryCategoryService categoryService) {
        this.userAdminService = userAdminService;
        this.productService = productService;
        this.orderService = orderService;
        this.couponService = couponService;
        this.categoryService = categoryService;
    }

    // -------------------------------------------------------------- accounts

    @GetMapping("/customers")
    @Operation(summary = "Search customer accounts")
    public Page<UserResponse> customers(@RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return userAdminService.list(CUSTOMER_ROLES, q, PageRequest.of(page, Math.min(size, 100)))
                .map(UserResponse::from);
    }

    @GetMapping("/staff")
    @Operation(summary = "Search admin and super admin accounts")
    public Page<UserResponse> staff(@RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return userAdminService.list(STAFF_ROLES, q, PageRequest.of(page, Math.min(size, 100)))
                .map(UserResponse::from);
    }

    @PostMapping("/customers")
    @Operation(summary = "Create a customer account")
    public ResponseEntity<UserResponse> createCustomer(@RequestBody UserForm form) {
        return ResponseEntity.status(201).body(UserResponse.from(userAdminService.createCustomer(form, CurrentUser.require())));
    }

    @PutMapping("/accounts/{id}")
    @Operation(summary = "Update a customer or admin's details (never their role)")
    public UserResponse updateAccount(@PathVariable Long id, @RequestBody UserForm form) {
        return UserResponse.from(userAdminService.updateDetails(id, form, CurrentUser.require()));
    }

    @PostMapping("/accounts/{id}/password")
    @Operation(summary = "Reset a password. Omit newPassword to have one generated and returned once.")
    public Map<String, String> resetPassword(@PathVariable Long id,
                                             @RequestParam(required = false) String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            return Map.of("generatedPassword", userAdminService.resetPasswordToGenerated(id, CurrentUser.require()));
        }
        userAdminService.resetPassword(id, newPassword, CurrentUser.require());
        return Map.of("status", "reset");
    }

    @PatchMapping("/accounts/{id}/enabled")
    @Operation(summary = "Block or unblock sign-in")
    public UserResponse setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return UserResponse.from(userAdminService.setEnabled(id, enabled, CurrentUser.require()));
    }

    // ------------------------------------------------- archive and hard delete

    @GetMapping("/archive/products")
    @Operation(summary = "Archived foods")
    public List<ProductResponse> archivedProducts() {
        return productService.archived().stream().map(ProductResponse::from).toList();
    }

    @PostMapping("/products/{id}/archive")
    @Operation(summary = "Archive a food (removes it from every listing, keeps the record)")
    public ProductResponse archiveProduct(@PathVariable Long id) {
        return ProductResponse.from(productService.archive(id));
    }

    @PostMapping("/products/{id}/restore")
    @Operation(summary = "Bring an archived food back")
    public ProductResponse restoreProduct(@PathVariable Long id) {
        return ProductResponse.from(productService.restore(id));
    }

    @DeleteMapping("/products/{id}")
    @Operation(summary = "Delete an archived food for good. Refused if any past order references it.")
    public Map<String, String> deleteProduct(@PathVariable Long id) {
        return Map.of("deleted", productService.deletePermanently(id));
    }

    @GetMapping("/archive/orders")
    @Operation(summary = "Archived orders")
    public List<OrderResponse> archivedOrders() {
        return orderService.archivedOrders().stream().map(OrderResponse::from).toList();
    }

    @PostMapping("/orders/{orderNumber}/archive")
    @Operation(summary = "Archive an order (unpaid orders are cancelled and their stock returned)")
    public OrderResponse archiveOrder(@PathVariable String orderNumber) {
        return OrderResponse.from(orderService.archive(orderNumber));
    }

    @PostMapping("/orders/{orderNumber}/restore")
    @Operation(summary = "Bring an archived order back")
    public OrderResponse restoreOrder(@PathVariable String orderNumber) {
        return OrderResponse.from(orderService.restore(orderNumber));
    }

    @DeleteMapping("/orders/{orderNumber}")
    @Operation(summary = "Delete an archived order, its lines and its payment records for good")
    public ResponseEntity<Void> deleteOrder(@PathVariable String orderNumber) {
        orderService.deletePermanently(orderNumber);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/archive/coupons")
    @Operation(summary = "Archived coupons")
    public List<CouponResponse> archivedCoupons() {
        return couponService.archived().stream().map(CouponResponse::from).toList();
    }

    @PostMapping("/coupons/{id}/archive")
    @Operation(summary = "Archive a coupon (customers can no longer apply it, nothing is deleted)")
    public CouponResponse archiveCoupon(@PathVariable Long id) {
        return CouponResponse.from(couponService.archive(id));
    }

    @PostMapping("/coupons/{id}/restore")
    @Operation(summary = "Bring an archived coupon back")
    public CouponResponse restoreCoupon(@PathVariable Long id) {
        return CouponResponse.from(couponService.restore(id));
    }

    @DeleteMapping("/coupons/{id}")
    @Operation(summary = "Delete an archived coupon for good. Refused if it has ever been redeemed.")
    public Map<String, String> deleteCoupon(@PathVariable Long id) {
        return Map.of("deleted", couponService.deletePermanently(id));
    }

    @GetMapping("/archive/categories")
    @Operation(summary = "Archived categories")
    public List<CategoryResponse> archivedCategories() {
        return categoryService.archived().stream()
                .map(c -> CategoryResponse.from(c, categoryService.productCount(c.getId())))
                .toList();
    }

    @PostMapping("/categories/{id}/archive")
    @Operation(summary = "Archive a category (refused if it is the only one left)")
    public CategoryResponse archiveCategory(@PathVariable Long id) {
        var saved = categoryService.archive(id);
        return CategoryResponse.from(saved, categoryService.productCount(id));
    }

    @PostMapping("/categories/{id}/restore")
    @Operation(summary = "Bring an archived category back")
    public CategoryResponse restoreCategory(@PathVariable Long id) {
        var saved = categoryService.restore(id);
        return CategoryResponse.from(saved, categoryService.productCount(id));
    }

    @DeleteMapping("/categories/{id}")
    @Operation(summary = "Delete an archived category for good. Refused while any product still lives in it.")
    public Map<String, String> deleteCategory(@PathVariable Long id) {
        return Map.of("deleted", categoryService.deletePermanently(id));
    }
}
