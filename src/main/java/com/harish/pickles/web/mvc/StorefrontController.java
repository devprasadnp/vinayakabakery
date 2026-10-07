package com.harish.pickles.web.mvc;

import com.harish.pickles.domain.CartItem;
import com.harish.pickles.domain.InventoryCategory;
import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.OrderStatus;
import com.harish.pickles.domain.Payment;
import com.harish.pickles.domain.PickleType;
import com.harish.pickles.domain.Product;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.Money;
import com.harish.pickles.dto.OrderDtos.AddressRequest;
import com.harish.pickles.dto.OrderDtos.CheckoutRequest;
import com.harish.pickles.dto.OrderDtos.OrderStatusResponse;
import com.harish.pickles.dto.OrderDtos.PaymentInitResponse;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.payment.PaymentProperties;
import com.harish.pickles.payment.QrCodeService;
import com.harish.pickles.repository.ProductRepository;
import com.harish.pickles.service.CartService;
import com.harish.pickles.service.CouponService;
import com.harish.pickles.service.CouponService.CouponPreview;
import com.harish.pickles.service.DeliveryPricingService;
import com.harish.pickles.service.DistanceService;
import com.harish.pickles.service.InventoryCategoryService;
import com.harish.pickles.service.OrderService;
import com.harish.pickles.service.PaymentService;
import com.harish.pickles.service.ProductService;
import com.harish.pickles.web.CurrentUser;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StorefrontController {

    private static final String PENDING_ORDER = "pendingOrderNumber";
    private static final String CART_COUPON = "cartCouponCode";

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final CartService cartService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentProperties paymentProperties;
    private final QrCodeService qrCodeService;
    private final CouponService couponService;
    private final InventoryCategoryService categoryService;
    private final DistanceService distanceService;
    private final DeliveryPricingService pricingService;

    public StorefrontController(ProductRepository productRepository, ProductService productService,
                                CartService cartService, OrderService orderService,
                                PaymentService paymentService, PaymentProperties paymentProperties,
                                QrCodeService qrCodeService, CouponService couponService,
                                InventoryCategoryService categoryService, DistanceService distanceService,
                                DeliveryPricingService pricingService) {
        this.distanceService = distanceService;
        this.pricingService = pricingService;
        this.productRepository = productRepository;
        this.productService = productService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.paymentProperties = paymentProperties;
        this.qrCodeService = qrCodeService;
        this.couponService = couponService;
        this.categoryService = categoryService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("categorySections", categorySections());
        return "index";
    }

    /** One section per storefront category that currently has at least one live food in it. */
    private record CategorySection(InventoryCategory category, long count, List<Product> products) {
    }

    private List<CategorySection> categorySections() {
        return categoryService.browsable().stream()
                .map(c -> Map.entry(c, categoryService.productCount(c.getId())))
                .filter(e -> e.getValue() > 0)
                .map(e -> new CategorySection(e.getKey(), e.getValue(),
                        productService.browse(null, e.getKey().getId(), null,
                                PageRequest.of(0, 4, Sort.by("name"))).getContent()))
                .toList();
    }

    @GetMapping("/products")
    public String products(@RequestParam(required = false) PickleType type,
                           @RequestParam(required = false) Long categoryId,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) String sort,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        Page<Product> results = productService.browse(type, categoryId, q,
                PageRequest.of(page, 12, ProductService.sortFor(sort)));
        model.addAttribute("products", results);
        model.addAttribute("type", type);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("selectedCategory",
                categoryId == null ? null : categoryService.browsable().stream()
                        .filter(c -> c.getId().equals(categoryId)).findFirst().orElse(null));
        model.addAttribute("q", q);
        model.addAttribute("sort", sort);
        model.addAttribute("varieties", productRepository.findDistinctCategories());
        model.addAttribute("inventoryCategories", accordionCategories());
        model.addAttribute("navActive", navKey(type));
        return "products";
    }

    /** Every browsable category paired with how many live foods sit in it, for the accordion. */
    private Map<InventoryCategory, Long> accordionCategories() {
        List<InventoryCategory> categories = categoryService.browsable();
        Map<InventoryCategory, Long> withCounts = new LinkedHashMap<>();
        categories.forEach(c -> withCounts.put(c, categoryService.productCount(c.getId())));
        return withCounts;
    }

    @GetMapping("/products/{slug}")
    public String product(@PathVariable String slug, Model model) {
        Product product = productService.getBySlug(slug);
        if (product.isArchived() || !product.isActive()) {
            throw new BusinessException("That food is no longer listed");
        }
        model.addAttribute("product", product);
        model.addAttribute("related", productService.featured(product.getPickleType(), 5));
        model.addAttribute("navActive", navKey(product.getPickleType()));
        return "product";
    }

    /** Which top-nav link to highlight. */
    private static String navKey(PickleType type) {
        if (type == null) {
            return "all";
        }
        return type == PickleType.VEG ? "veg" : "nonveg";
    }

    // ------------------------------------------------------------------ cart

    @GetMapping("/cart")
    public String cart(Model model, HttpSession session) {
        User user = CurrentUser.require();
        model.addAttribute("cart", cartService.summary(user));
        addCouponAttributes(model, session, user);
        return "cart";
    }

    // ---------------------------------------------------------------- coupon

    @PostMapping("/cart/coupon")
    public String applyCoupon(@RequestParam String code,
                              @RequestParam(defaultValue = "/cart") String returnTo,
                              HttpSession session, RedirectAttributes flash) {
        User user = CurrentUser.require();
        try {
            long subtotalPaise = cartSubtotalPaise(user);
            if (subtotalPaise <= 0) {
                throw new BusinessException("Add something to your cart first");
            }
            CouponPreview preview = couponService.preview(code, subtotalPaise, user);
            session.setAttribute(CART_COUPON, preview.code());
            flash.addFlashAttribute("message",
                    preview.code() + " applied — you saved " + Money.format(preview.discountPaise()));
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + safeReturnTo(returnTo);
    }

    @PostMapping("/cart/coupon/remove")
    public String removeCoupon(@RequestParam(defaultValue = "/cart") String returnTo,
                               HttpSession session, RedirectAttributes flash) {
        session.removeAttribute(CART_COUPON);
        flash.addFlashAttribute("message", "Coupon removed");
        return "redirect:" + safeReturnTo(returnTo);
    }

    private static String safeReturnTo(String returnTo) {
        return "/checkout".equals(returnTo) ? "/checkout" : "/cart";
    }

    private long cartSubtotalPaise(User user) {
        return cartService.items(user).stream().mapToLong(CartItem::getLineTotalPaise).sum();
    }

    /** Re-checks a coupon already in the session against the current cart and exposes it to the model. */
    private void addCouponAttributes(Model model, HttpSession session, User user) {
        String code = (String) session.getAttribute(CART_COUPON);
        if (code == null) {
            return;
        }
        try {
            CouponPreview preview = couponService.preview(code, cartSubtotalPaise(user), user);
            model.addAttribute("appliedCoupon", preview.code());
            model.addAttribute("couponDiscount", Money.toRupees(preview.discountPaise()));
        } catch (BusinessException ex) {
            session.removeAttribute(CART_COUPON);
            model.addAttribute("error", "Your coupon no longer applies: " + ex.getMessage());
        }
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam Long productId,
                            @RequestParam(defaultValue = "1") int quantity,
                            RedirectAttributes flash) {
        try {
            cartService.increment(CurrentUser.require(), productId, quantity);
            flash.addFlashAttribute("message", "Added to your cart");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateCart(@RequestParam Long productId, @RequestParam int quantity, RedirectAttributes flash) {
        try {
            if (quantity <= 0) {
                cartService.remove(CurrentUser.require(), productId);
            } else {
                cartService.addOrUpdate(CurrentUser.require(), productId, quantity);
            }
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam Long productId) {
        cartService.remove(CurrentUser.require(), productId);
        return "redirect:/cart";
    }

    // -------------------------------------------------------------- checkout

    @GetMapping("/checkout")
    public String checkout(Model model, HttpSession session) {
        User user = CurrentUser.require();
        var cart = cartService.summary(user);
        if (cart.totalItems() == 0) {
            return "redirect:/cart";
        }
        model.addAttribute("cart", cart);
        model.addAttribute("gateway", paymentService.gatewayName());
        model.addAttribute("qrMode", paymentProperties.isQrMode());
        addCouponAttributes(model, session, user);
        return "checkout";
    }

    /** What a customer would pay for delivery to a dropped pin, shown live on the checkout page. */
    public record ShippingQuote(Double distanceKm, java.math.BigDecimal shipping, java.math.BigDecimal total,
                                boolean free, String message) {
    }

    @GetMapping("/checkout/quote")
    @ResponseBody
    public ShippingQuote quote(@RequestParam(required = false) Double latitude,
                               @RequestParam(required = false) Double longitude) {
        User user = CurrentUser.require();
        long subtotal = cartSubtotalPaise(user);
        Double km = distanceService.distanceKm(latitude, longitude).orElse(null);
        String message = null;
        try {
            pricingService.requireWithinRange(km);
        } catch (BusinessException ex) {
            message = ex.getMessage();
        }
        long shipping = cartService.shippingFor(subtotal, km);
        return new ShippingQuote(km, Money.toRupees(shipping), Money.toRupees(subtotal + shipping),
                subtotal > 0 && shipping == 0, message);
    }

    /** Live position of the courier for the customer's own order; polled by the order page. */
    @GetMapping("/orders/{orderNumber}/tracking")
    @ResponseBody
    public OrderService.Tracking tracking(@PathVariable String orderNumber) {
        return orderService.trackingFor(orderNumber, CurrentUser.require());
    }

    @PostMapping("/checkout")
    public String placeOrder(@RequestParam String contactName,
                             @RequestParam String contactEmail,
                             @RequestParam String contactPhone,
                             @RequestParam String addressLine1,
                             @RequestParam(required = false) String addressLine2,
                             @RequestParam String city,
                             @RequestParam String state,
                             @RequestParam String pincode,
                             @RequestParam(required = false) Double latitude,
                             @RequestParam(required = false) Double longitude,
                             @RequestParam(required = false) String notes,
                             HttpSession session,
                             RedirectAttributes flash) {
        User user = CurrentUser.require();
        try {
            CheckoutRequest request = new CheckoutRequest(
                    new AddressRequest(contactName, contactEmail, contactPhone,
                            addressLine1, addressLine2, city, state, pincode, latitude, longitude),
                    notes);
            Order order = orderService.placeOrder(user, request);

            String couponCode = (String) session.getAttribute(CART_COUPON);
            if (couponCode != null) {
                try {
                    couponService.redeem(couponCode, order, user);
                    flash.addFlashAttribute("message", couponCode + " applied — you saved "
                            + Money.format(order.getDiscountPaise()));
                } catch (BusinessException ex) {
                    flash.addFlashAttribute("error", couponCode + " could not be applied: " + ex.getMessage());
                }
                session.removeAttribute(CART_COUPON);
            }

            if (paymentProperties.isQrMode()) {
                // Manual UPI-QR flow: stock is already held by placeOrder(), no gateway call.
                // Mark the order as awaiting payment and send the customer to the scan-to-pay page.
                orderService.updateStatus(order.getOrderNumber(), OrderStatus.PENDING_PAYMENT);
                return "redirect:/orders/" + order.getOrderNumber() + "/pay-qr";
            }

            PaymentInitResponse payment = paymentService.initiate(order);
            session.setAttribute(PENDING_ORDER, order.getOrderNumber());
            return "redirect:" + payment.redirectUrl();
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/checkout";
        }
    }

    /**
     * The "scan to pay" page shown right after an order is placed in QR mode, and revisitable
     * from the order detail page. Nothing here detects the payment itself — a human still marks
     * the order PAID from the admin panel once the UPI transfer with this order number lands —
     * but the page polls {@link #payQrStatus} and moves on by itself the moment that happens.
     */
    @GetMapping("/orders/{orderNumber}/pay-qr")
    public String payWithQr(@PathVariable String orderNumber, Model model, RedirectAttributes flash) {
        Order order = orderService.getForUser(orderNumber, CurrentUser.require());
        // The moment a customer goes to pay, an order for an area we no longer deliver to is cancelled.
        if (cancelledForDeliveryArea(order, flash)) {
            return "redirect:/orders/" + orderNumber;
        }
        // A cancelled, paid or shipped order must never show a live payment QR.
        if (!order.isPayable()) {
            flash.addFlashAttribute("error", "Order " + orderNumber + " is " + order.getStatus()
                    + " and cannot be paid.");
            return "redirect:/orders/" + orderNumber;
        }
        model.addAttribute("order", order);
        model.addAttribute("paymentProperties", paymentProperties);
        // In PAYTM mode the QR is Paytm's own (confirmed automatically); otherwise it is our
        // static store UPI ID (confirmed by hand). Either way /qr-code.png renders the right one.
        model.addAttribute("gatewayQr", paymentProperties.getMode() == PaymentProperties.Mode.PAYTM);
        if (paymentProperties.getMode() != PaymentProperties.Mode.PAYTM) {
            model.addAttribute("upiUri", qrCodeService.buildUpiUri(paymentProperties, order.getOrderNumber(),
                    Money.toRupees(order.getTotalPaise())));
        }
        return "pay-qr";
    }

    /**
     * Cheap poll target for the "scan to pay" page's session-authenticated fetch — the JWT-only
     * {@code /api/**} chain can't be reached with just the browser's session cookie. While a
     * gateway (not the manual QR) is backing this order, each poll also asks the gateway itself
     * what happened, so the page moves on even if the webhook has not arrived yet.
     */
    @GetMapping("/orders/{orderNumber}/pay-qr-status")
    @ResponseBody
    public OrderStatusResponse payQrStatus(@PathVariable String orderNumber) {
        Order order = orderService.getForUser(orderNumber, CurrentUser.require());
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && paymentProperties.getMode() != PaymentProperties.Mode.QR) {
            try {
                paymentService.reconcile(orderNumber);
            } catch (RuntimeException ignored) {
                // Best-effort: the webhook (or the next poll) will catch it up.
            }
            order = orderService.getForUser(orderNumber, CurrentUser.require());
        }
        return new OrderStatusResponse(order.getOrderNumber(), order.getStatus());
    }

    /**
     * The QR code shown on the "scan to pay" page: in PAYTM mode, the gateway's own dynamic QR
     * for this exact order (its payload is stored on the {@link Payment} row when the payment
     * was initiated); otherwise a UPI deep link built from the store's static UPI ID, with the
     * order number baked in as the transaction note so it carries through automatically.
     */
    @GetMapping(value = "/orders/{orderNumber}/qr-code.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> orderQrCode(@PathVariable String orderNumber) {
        Order order = orderService.getForUser(orderNumber, CurrentUser.require());
        if (!order.isPayable() || !orderService.isDeliverable(order)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        String qrContent;
        if (paymentProperties.getMode() == PaymentProperties.Mode.PAYTM) {
            Payment payment = paymentService.findByMerchantOrderId(orderNumber).orElse(null);
            if (payment == null || payment.getQrPayload() == null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            qrContent = payment.getQrPayload();
        } else {
            qrContent = qrCodeService.buildUpiUri(paymentProperties, order.getOrderNumber(),
                    Money.toRupees(order.getTotalPaise()));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(qrCodeService.generatePng(qrContent));
    }

    /**
     * PhonePe returns the customer's browser here. The redirect carries no trustworthy status,
     * so we always re-check with the gateway before showing anything.
     */
    @GetMapping("/payment/callback")
    public String paymentCallback(@RequestParam(required = false) String merchantOrderId,
                                  HttpSession session, RedirectAttributes flash) {
        String orderNumber = merchantOrderId != null
                ? merchantOrderId
                : (String) session.getAttribute(PENDING_ORDER);
        if (orderNumber == null) {
            flash.addFlashAttribute("error", "We could not match that payment to an order");
            return "redirect:/orders";
        }
        session.removeAttribute(PENDING_ORDER);
        try {
            paymentService.reconcile(orderNumber);
        } catch (RuntimeException ex) {
            flash.addFlashAttribute("error", "Could not confirm the payment yet: " + ex.getMessage());
        }
        return "redirect:/orders/" + orderNumber;
    }

    // ---------------------------------------------------------------- orders

    @GetMapping("/orders")
    public String orders(Model model) {
        model.addAttribute("orders", orderService.myOrders(CurrentUser.require()));
        return "orders";
    }

    @GetMapping("/orders/{orderNumber}")
    public String order(@PathVariable String orderNumber, Model model) {
        Order order = orderService.getForUser(orderNumber, CurrentUser.require());
        model.addAttribute("order", order);
        model.addAttribute("payment", paymentService.findByMerchantOrderId(orderNumber).orElse(null));
        model.addAttribute("qrMode", paymentProperties.isQrMode());
        model.addAttribute("paymentProperties", paymentProperties);
        return "order-detail";
    }

    @PostMapping("/orders/{orderNumber}/retry-payment")
    public String retryPayment(@PathVariable String orderNumber, HttpSession session, RedirectAttributes flash) {
        Order order = orderService.getForUser(orderNumber, CurrentUser.require());
        if (cancelledForDeliveryArea(order, flash)) {
            return "redirect:/orders/" + orderNumber;
        }
        if (paymentProperties.isQrMode()) {
            return "redirect:/orders/" + orderNumber + "/pay-qr";
        }
        try {
            PaymentInitResponse payment = paymentService.initiate(order);
            session.setAttribute(PENDING_ORDER, order.getOrderNumber());
            return "redirect:" + payment.redirectUrl();
        } catch (RuntimeException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/orders/" + orderNumber;
        }
    }

    /**
     * Re-checks the delivery area for an unpaid order. When we no longer deliver there the order has
     * just been cancelled: tell the customer why, and let the caller send them to the order page.
     */
    private boolean cancelledForDeliveryArea(Order order, RedirectAttributes flash) {
        if (orderService.cancelIfOutsideDeliveryArea(order.getOrderNumber())) {
            flash.addFlashAttribute("error", OrderService.outsideAreaMessage(order));
            return true;
        }
        return false;
    }
}
