package com.harish.pickles.web.rest;

import com.harish.pickles.domain.OrderStatus;
import com.harish.pickles.domain.PickleType;
import com.harish.pickles.dto.CategoryDtos.CategoryRequest;
import com.harish.pickles.dto.CategoryDtos.CategoryResponse;
import com.harish.pickles.dto.CouponDtos.CouponRequest;
import com.harish.pickles.dto.CouponDtos.CouponResponse;
import com.harish.pickles.dto.OrderDtos.OrderResponse;
import com.harish.pickles.dto.ProductDtos.ProductRequest;
import com.harish.pickles.dto.ProductDtos.ProductResponse;
import com.harish.pickles.service.CouponService;
import com.harish.pickles.service.InventoryCategoryService;
import com.harish.pickles.service.OrderService;
import com.harish.pickles.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminRestController {

    private final ProductService productService;
    private final OrderService orderService;
    private final CouponService couponService;
    private final InventoryCategoryService categoryService;

    public AdminRestController(ProductService productService, OrderService orderService,
                               CouponService couponService, InventoryCategoryService categoryService) {
        this.productService = productService;
        this.orderService = orderService;
        this.couponService = couponService;
        this.categoryService = categoryService;
    }

    @GetMapping("/products")
    @Operation(summary = "Catalogue, including products hidden from the storefront")
    public Page<ProductResponse> products(@RequestParam(required = false) PickleType type,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) String sort,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return productService.catalogue(type, categoryId, q, null,
                        PageRequest.of(page, Math.min(size, 100), ProductService.sortFor(sort)))
                .map(ProductResponse::from);
    }

    @PostMapping("/products")
    @Operation(summary = "Add a food")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(201).body(ProductResponse.from(productService.create(request)));
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Update a food")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.update(id, request));
    }

    @PatchMapping("/products/{id}/active")
    @Operation(summary = "Show or hide a food on the storefront")
    public ProductResponse setActive(@PathVariable Long id, @RequestParam boolean active) {
        productService.setActive(id, active);
        return ProductResponse.from(productService.getById(id));
    }

    // ---------------------------------------------------------------- photos

    @PostMapping(value = "/products/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload one or more photos for a food (JPG, PNG, WEBP or GIF)")
    public ProductResponse uploadPhotos(@PathVariable Long id,
                                        @RequestPart("photos") MultipartFile[] photos) {
        productService.addImages(id, photos);
        return ProductResponse.from(productService.getById(id));
    }

    @PatchMapping("/products/{id}/photos/{imageId}/primary")
    @Operation(summary = "Choose the photo shown in listings")
    public ProductResponse makePrimary(@PathVariable Long id, @PathVariable Long imageId) {
        productService.setPrimaryImage(id, imageId);
        return ProductResponse.from(productService.getById(id));
    }

    @DeleteMapping("/products/{id}/photos/{imageId}")
    @Operation(summary = "Remove a photo and its file on disk")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long id, @PathVariable Long imageId) {
        productService.deleteImage(id, imageId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- orders

    @GetMapping("/orders")
    @Operation(summary = "All live orders, newest first")
    public Page<OrderResponse> orders(@RequestParam(required = false) OrderStatus status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return orderService.allOrders(status, PageRequest.of(page, Math.min(size, 100)))
                .map(OrderResponse::from);
    }

    @PatchMapping("/orders/{orderNumber}/status")
    @Operation(summary = "Move an order along (PACKED, SHIPPED, DELIVERED, CANCELLED...)")
    public OrderResponse updateStatus(@PathVariable String orderNumber, @RequestParam OrderStatus status) {
        return OrderResponse.from(orderService.updateStatus(orderNumber, status));
    }

    @GetMapping("/products/{id}/references")
    @Operation(summary = "How many past order lines use this food (0 means it can be deleted)")
    public Map<String, Long> references(@PathVariable Long id) {
        return Map.of("orderLines", productService.orderReferences(id));
    }

    // --------------------------------------------------------------- coupons

    @GetMapping("/coupons")
    @Operation(summary = "Coupons, including paused ones")
    public Page<CouponResponse> coupons(@RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return couponService.catalogue(q, PageRequest.of(page, Math.min(size, 100))).map(CouponResponse::from);
    }

    @PostMapping("/coupons")
    @Operation(summary = "Create a coupon")
    public ResponseEntity<CouponResponse> createCoupon(@Valid @RequestBody CouponRequest request) {
        return ResponseEntity.status(201).body(CouponResponse.from(couponService.create(request)));
    }

    @PutMapping("/coupons/{id}")
    @Operation(summary = "Update a coupon's terms")
    public CouponResponse updateCoupon(@PathVariable Long id, @Valid @RequestBody CouponRequest request) {
        return CouponResponse.from(couponService.update(id, request));
    }

    @PatchMapping("/coupons/{id}/active")
    @Operation(summary = "Pause or resume a coupon")
    public CouponResponse setCouponActive(@PathVariable Long id, @RequestParam boolean active) {
        return CouponResponse.from(couponService.setActive(id, active));
    }

    // ------------------------------------------------------------- categories

    @GetMapping("/categories")
    @Operation(summary = "Inventory categories (accordion sections), including hidden ones")
    public List<CategoryResponse> categories() {
        return categoryService.all().stream()
                .map(c -> CategoryResponse.from(c, categoryService.productCount(c.getId())))
                .toList();
    }

    @PostMapping("/categories")
    @Operation(summary = "Add a category")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        var saved = categoryService.create(request);
        return ResponseEntity.status(201).body(CategoryResponse.from(saved, 0));
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "Update a category")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        var saved = categoryService.update(id, request);
        return CategoryResponse.from(saved, categoryService.productCount(id));
    }

    @PatchMapping("/categories/{id}/active")
    @Operation(summary = "Show or hide a category")
    public CategoryResponse setCategoryActive(@PathVariable Long id, @RequestParam boolean active) {
        var saved = categoryService.setActive(id, active);
        return CategoryResponse.from(saved, categoryService.productCount(id));
    }
}
