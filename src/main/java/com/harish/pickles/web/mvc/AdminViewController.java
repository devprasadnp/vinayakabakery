package com.harish.pickles.web.mvc;

import com.harish.pickles.domain.DiscountType;
import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.OrderStatus;
import com.harish.pickles.domain.PickleType;
import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.Product;
import com.harish.pickles.domain.SpiceLevel;
import com.harish.pickles.dto.CategoryDtos.CategoryRequest;
import com.harish.pickles.dto.CouponDtos.CouponRequest;
import com.harish.pickles.dto.Money;
import com.harish.pickles.dto.ProductDtos.ProductRequest;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.repository.OrderRepository;
import com.harish.pickles.repository.ProductRepository;
import com.harish.pickles.repository.UserRepository;
import com.harish.pickles.service.CouponService;
import com.harish.pickles.service.InventoryCategoryService;
import com.harish.pickles.service.OrderService;
import com.harish.pickles.service.PaymentService;
import com.harish.pickles.service.ProductService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminViewController {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final CouponService couponService;
    private final InventoryCategoryService categoryService;
    private final UserRepository userRepository;

    public AdminViewController(ProductService productService, ProductRepository productRepository,
                               OrderService orderService, OrderRepository orderRepository,
                               PaymentService paymentService, CouponService couponService,
                               InventoryCategoryService categoryService, UserRepository userRepository) {
        this.productService = productService;
        this.productRepository = productRepository;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.couponService = couponService;
        this.categoryService = categoryService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("productCount", productRepository.countByArchivedFalse());
        model.addAttribute("vegCount", productRepository.countByPickleTypeAndArchivedFalse(PickleType.VEG));
        model.addAttribute("nonVegCount", productRepository.countByPickleTypeAndArchivedFalse(PickleType.NON_VEG));
        model.addAttribute("lowStock", productRepository.countByArchivedFalseAndStockQtyLessThan(5));
        model.addAttribute("orderCount", orderRepository.countByArchivedFalse());
        model.addAttribute("paidOrders", orderRepository.countByStatusAndArchivedFalse(OrderStatus.PAID));
        model.addAttribute("pendingOrders",
                orderRepository.countByStatusAndArchivedFalse(OrderStatus.PENDING_PAYMENT));
        model.addAttribute("revenue", Money.toRupees(orderRepository.sumTotalPaiseByStatusIn(
                List.of(OrderStatus.PAID, OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.DELIVERED))));
        model.addAttribute("recentOrders", orderService.allOrders(null, PageRequest.of(0, 8)).getContent());
        model.addAttribute("activePage", "dashboard");
        return "admin/dashboard";
    }

    // -------------------------------------------------------------- products

    @GetMapping("/products")
    public String products(@RequestParam(required = false) PickleType type,
                           @RequestParam(required = false) Long categoryId,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) String sort,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        model.addAttribute("products",
                productService.catalogue(type, categoryId, q, null,
                        PageRequest.of(page, 20, ProductService.sortFor(sort))));
        model.addAttribute("type", type);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("q", q);
        model.addAttribute("sort", sort);
        model.addAttribute("categories", categoryService.all());
        model.addAttribute("activePage", "products");
        return "admin/products";
    }

    @GetMapping("/products/new")
    public String newProduct(Model model) {
        model.addAttribute("product", null);
        addFormOptions(model);
        return "admin/product-form";
    }

    @GetMapping("/products/{id}/edit")
    public String editProduct(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getById(id));
        addFormOptions(model);
        return "admin/product-form";
    }

    @PostMapping("/products")
    public String saveProduct(@RequestParam(required = false) Long id,
                              @RequestParam String sku,
                              @RequestParam String name,
                              @RequestParam(required = false) String description,
                              @RequestParam PickleType pickleType,
                              @RequestParam String category,
                              @RequestParam Long inventoryCategoryId,
                              @RequestParam SpiceLevel spiceLevel,
                              @RequestParam BigDecimal price,
                              @RequestParam(required = false) BigDecimal mrp,
                              @RequestParam Integer weightGrams,
                              @RequestParam(required = false) Integer shelfLifeDays,
                              @RequestParam(required = false) String ingredients,
                              @RequestParam(required = false) String imageUrl,
                              @RequestParam Integer stockQty,
                              @RequestParam(defaultValue = "false") boolean active,
                              @RequestParam(name = "photos", required = false) MultipartFile[] photos,
                              RedirectAttributes flash) {
        ProductRequest request = new ProductRequest(sku, name, description, pickleType, category,
                inventoryCategoryId, spiceLevel,
                price, mrp, weightGrams, shelfLifeDays, ingredients, imageUrl, stockQty, active);
        Product saved;
        try {
            saved = (id == null) ? productService.create(request) : productService.update(id, request);
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return id == null ? "redirect:/admin/products/new" : "redirect:/admin/products/" + id + "/edit";
        }

        String note = saved.getName() + " saved";
        try {
            int added = productService.addImages(saved.getId(), photos);
            if (added > 0) {
                note += " with " + added + " new photo" + (added == 1 ? "" : "s");
            }
        } catch (BusinessException ex) {
            flash.addFlashAttribute("message", note);
            flash.addFlashAttribute("error", "Photos: " + ex.getMessage());
            return "redirect:/admin/products/" + saved.getId() + "/edit";
        }
        flash.addFlashAttribute("message", note);
        return "redirect:/admin/products/" + saved.getId() + "/edit";
    }

    @PostMapping("/products/{id}/toggle")
    public String toggleProduct(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes flash) {
        try {
            productService.setActive(id, active);
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    // ---------------------------------------------------------------- photos

    @PostMapping("/products/{id}/photos")
    public String uploadPhotos(@PathVariable Long id,
                               @RequestParam("photos") MultipartFile[] photos,
                               RedirectAttributes flash) {
        try {
            int added = productService.addImages(id, photos);
            flash.addFlashAttribute("message", added == 0
                    ? "No files were selected"
                    : added + " photo" + (added == 1 ? "" : "s") + " uploaded");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/products/" + id + "/edit";
    }

    @PostMapping("/products/{id}/photos/{imageId}/primary")
    public String makePrimary(@PathVariable Long id, @PathVariable Long imageId, RedirectAttributes flash) {
        productService.setPrimaryImage(id, imageId);
        flash.addFlashAttribute("message", "Main photo updated");
        return "redirect:/admin/products/" + id + "/edit";
    }

    @PostMapping("/products/{id}/photos/{imageId}/delete")
    public String deletePhoto(@PathVariable Long id, @PathVariable Long imageId, RedirectAttributes flash) {
        productService.deleteImage(id, imageId);
        flash.addFlashAttribute("message", "Photo removed");
        return "redirect:/admin/products/" + id + "/edit";
    }

    // ---------------------------------------------------------------- orders

    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) OrderStatus status,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        var orders = orderService.allOrders(status, PageRequest.of(page, 20));
        model.addAttribute("orders", orders);
        model.addAttribute("payments", paymentService.findByMerchantOrderIds(
                orders.getContent().stream().map(Order::getOrderNumber).toList()));
        model.addAttribute("status", status);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("partners", userRepository.findByRoleAndEnabledTrueOrderByFullNameAsc(Role.DELIVERY_PARTNER));
        model.addAttribute("activePage", "orders");
        return "admin/orders";
    }

    /** Hand an order to a delivery partner; a blank partnerId takes it back. */
    @PostMapping("/orders/{orderNumber}/assign")
    public String assignPartner(@PathVariable String orderNumber,
                                @RequestParam(required = false) Long partnerId,
                                RedirectAttributes flash) {
        try {
            Order order = orderService.assignDeliveryPartner(orderNumber, partnerId);
            flash.addFlashAttribute("message", partnerId == null
                    ? orderNumber + " is no longer assigned to a delivery partner"
                    : orderNumber + " assigned to " + order.getDeliveryPartner().getFullName());
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/orders";
    }

    @PostMapping("/orders/{orderNumber}/status")
    public String updateStatus(@PathVariable String orderNumber, @RequestParam OrderStatus status,
                               RedirectAttributes flash) {
        orderService.updateStatus(orderNumber, status);
        flash.addFlashAttribute("message", orderNumber + " is now " + status);
        return "redirect:/admin/orders";
    }

    @PostMapping("/orders/{orderNumber}/confirm-payment")
    public String confirmPayment(@PathVariable String orderNumber, @RequestParam String transactionId,
                                 @RequestParam BigDecimal amount, RedirectAttributes flash) {
        try {
            paymentService.confirmManualPayment(orderNumber, transactionId, Money.toPaise(amount));
            flash.addFlashAttribute("message", orderNumber + " confirmed as PAID (ref " + transactionId + ")");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/orders";
    }

    // --------------------------------------------------------------- coupons

    @GetMapping("/coupons")
    public String coupons(@RequestParam(required = false) String q,
                          @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("coupons", couponService.catalogue(q, PageRequest.of(page, 20)));
        model.addAttribute("q", q);
        model.addAttribute("activePage", "coupons");
        return "admin/coupons";
    }

    @GetMapping("/coupons/new")
    public String newCoupon(Model model) {
        model.addAttribute("coupon", null);
        model.addAttribute("discountTypes", DiscountType.values());
        model.addAttribute("activePage", "coupons");
        return "admin/coupon-form";
    }

    @GetMapping("/coupons/{id}/edit")
    public String editCoupon(@PathVariable Long id, Model model) {
        model.addAttribute("coupon", couponService.getById(id));
        model.addAttribute("discountTypes", DiscountType.values());
        model.addAttribute("activePage", "coupons");
        return "admin/coupon-form";
    }

    @PostMapping("/coupons")
    public String saveCoupon(@RequestParam(required = false) Long id,
                             @RequestParam String code,
                             @RequestParam(required = false) String description,
                             @RequestParam DiscountType discountType,
                             @RequestParam(required = false) Integer percentOff,
                             @RequestParam(required = false) BigDecimal flatOffRupees,
                             @RequestParam(required = false) BigDecimal maxDiscountRupees,
                             @RequestParam(required = false) BigDecimal minOrderRupees,
                             @RequestParam(required = false) Integer totalUsageLimit,
                             @RequestParam(required = false) Integer perCustomerLimit,
                             @RequestParam(required = false) String startAt,
                             @RequestParam(required = false) String endAt,
                             @RequestParam(defaultValue = "false") boolean active,
                             RedirectAttributes flash) {
        try {
            CouponRequest request = new CouponRequest(code, description, discountType, percentOff,
                    flatOffRupees, maxDiscountRupees, minOrderRupees, totalUsageLimit, perCustomerLimit,
                    parseDateTime(startAt, "start date"), parseDateTime(endAt, "end date"), active);
            var saved = (id == null) ? couponService.create(request) : couponService.update(id, request);
            flash.addFlashAttribute("message", saved.getCode() + " saved");
            return "redirect:/admin/coupons/" + saved.getId() + "/edit";
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return id == null ? "redirect:/admin/coupons/new" : "redirect:/admin/coupons/" + id + "/edit";
        }
    }

    @PostMapping("/coupons/{id}/toggle")
    public String toggleCoupon(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes flash) {
        var coupon = couponService.setActive(id, active);
        flash.addFlashAttribute("message", coupon.getCode() + (active ? " is now active" : " is now paused"));
        return "redirect:/admin/coupons";
    }

    /** HTML {@code datetime-local} inputs post "yyyy-MM-ddTHH:mm"; blank means "no limit". */
    private static LocalDateTime parseDateTime(String value, String fieldLabel) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("That " + fieldLabel + " does not look right");
        }
    }

    private void addFormOptions(Model model) {
        model.addAttribute("pickleTypes", PickleType.values());
        model.addAttribute("spiceLevels", SpiceLevel.values());
        model.addAttribute("categories", categoryService.all());
        model.addAttribute("activePage", "products");
    }

    // ------------------------------------------------------------- categories

    @GetMapping("/categories")
    public String categories(Model model) {
        List<com.harish.pickles.domain.InventoryCategory> categories = categoryService.all();
        java.util.Map<Long, Long> categoryCounts = new java.util.LinkedHashMap<>();
        categories.forEach(c -> categoryCounts.put(c.getId(), categoryService.productCount(c.getId())));
        model.addAttribute("categories", categories);
        model.addAttribute("categoryCounts", categoryCounts);
        model.addAttribute("activePage", "categories");
        return "admin/categories";
    }

    @GetMapping("/categories/new")
    public String newCategory(Model model) {
        model.addAttribute("category", null);
        model.addAttribute("activePage", "categories");
        return "admin/category-form";
    }

    @GetMapping("/categories/{id}/edit")
    public String editCategory(@PathVariable Long id, Model model) {
        model.addAttribute("category", categoryService.getById(id));
        model.addAttribute("activePage", "categories");
        return "admin/category-form";
    }

    @PostMapping("/categories")
    public String saveCategory(@RequestParam(required = false) Long id,
                               @RequestParam String name,
                               @RequestParam(required = false) String icon,
                               @RequestParam(required = false) String veg,
                               @RequestParam(required = false) Integer displayOrder,
                               @RequestParam(defaultValue = "false") boolean active,
                               RedirectAttributes flash) {
        try {
            Boolean vegFlag = (veg == null || veg.isBlank()) ? null : Boolean.valueOf(veg);
            CategoryRequest request = new CategoryRequest(name, icon, vegFlag, displayOrder, active);
            var saved = (id == null) ? categoryService.create(request) : categoryService.update(id, request);
            flash.addFlashAttribute("message", saved.getName() + " saved");
            return "redirect:/admin/categories";
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return id == null ? "redirect:/admin/categories/new" : "redirect:/admin/categories/" + id + "/edit";
        }
    }

    @PostMapping("/categories/{id}/toggle")
    public String toggleCategory(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes flash) {
        try {
            var category = categoryService.setActive(id, active);
            flash.addFlashAttribute("message",
                    category.getName() + (active ? " is now shown" : " is now hidden"));
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }
}
