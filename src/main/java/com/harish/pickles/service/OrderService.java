package com.harish.pickles.service;

import com.harish.pickles.domain.CartItem;
import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.OrderItem;
import com.harish.pickles.domain.OrderStatus;
import com.harish.pickles.domain.Product;
import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.ShippingAddress;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.OrderDtos.AddressRequest;
import com.harish.pickles.dto.OrderDtos.CheckoutRequest;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.exception.NotFoundException;
import com.harish.pickles.repository.OrderRepository;
import com.harish.pickles.repository.ProductRepository;
import com.harish.pickles.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;
    private final DeliveryZoneService deliveryZoneService;
    private final UserRepository userRepository;
    private final DistanceService distanceService;
    private final DeliveryPricingService pricingService;
    private final DeliveryPinValidator pinValidator;

    /** What the customer's tracking map shows: the courier's last position while the order is out. */
    public record Tracking(String status, boolean live, Double latitude, Double longitude,
                           LocalDateTime updatedAt, String partnerName, Double distanceKm) {
    }

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        CartService cartService, DeliveryZoneService deliveryZoneService,
                        UserRepository userRepository, DistanceService distanceService,
                        DeliveryPricingService pricingService, DeliveryPinValidator pinValidator) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
        this.deliveryZoneService = deliveryZoneService;
        this.userRepository = userRepository;
        this.distanceService = distanceService;
        this.pricingService = pricingService;
        this.pinValidator = pinValidator;
    }

    /**
     * The courier's browser reports its position while an order is out for delivery. Only the
     * assigned partner, and only while the order is SHIPPED - never a stray ping after delivery.
     */
    @Transactional
    public void updatePartnerLocation(String orderNumber, User partner, double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BusinessException("Invalid coordinates");
        }
        Order order = getByOrderNumber(orderNumber);
        if (order.isArchived() || order.getDeliveryPartner() == null
                || !order.getDeliveryPartner().getId().equals(partner.getId())) {
            throw NotFoundException.of("Order", orderNumber);
        }
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new BusinessException(orderNumber + " is not out for delivery");
        }
        order.setPartnerLatitude(lat);
        order.setPartnerLongitude(lng);
        order.setPartnerLocationAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    /** Tracking for the customer's own order (or staff). Position only while the order is SHIPPED. */
    @Transactional(readOnly = true)
    public Tracking trackingFor(String orderNumber, User user) {
        Order order = getForUser(orderNumber, user);
        boolean live = order.getStatus() == OrderStatus.SHIPPED && order.getPartnerLatitude() != null;
        String partner = order.getDeliveryPartner() == null ? null : order.getDeliveryPartner().getFullName();
        return new Tracking(order.getStatus().name(), live,
                live ? order.getPartnerLatitude() : null, live ? order.getPartnerLongitude() : null,
                live ? order.getPartnerLocationAt() : null, partner, order.getDistanceKm());
    }

    /**
     * Turns the signed-in customer's cart into an order and reserves stock.
     * Stock is released again by {@link #releaseStock(Order)} if the payment fails.
     *
     * <p>The delivery PIN code is checked first, before any stock moves, so an address we cannot
     * serve is turned away here - never after the customer has been sent to pay.</p>
     */
    @Transactional
    public Order placeOrder(User user, CheckoutRequest request) {
        List<CartItem> cart = cartService.items(user);
        if (cart.isEmpty()) {
            throw new BusinessException("Your cart is empty");
        }
        deliveryZoneService.requireDeliverable(request.address().pincode());

        Order order = new Order();
        order.setOrderNumber(nextOrderNumber());
        order.setUser(user);
        order.setStatus(OrderStatus.CREATED);
        ShippingAddress address = toAddress(request.address());
        address.setPinAddress(truncate(pinValidator.validate(request.address()), 300));
        order.setShippingAddress(address);
        order.setNotes(request.notes());

        for (CartItem line : cart) {
            Product product = productRepository.findById(line.getProduct().getId())
                    .orElseThrow(() -> NotFoundException.of("Product", line.getProduct().getId()));
            if (!product.isActive() || product.isArchived()) {
                throw new BusinessException(product.getName() + " is no longer available");
            }
            if (product.getStockQty() < line.getQuantity()) {
                throw new BusinessException("Only " + product.getStockQty() + " jar(s) of "
                        + product.getName() + " left in stock");
            }
            product.setStockQty(product.getStockQty() - line.getQuantity());
            productRepository.save(product);
            order.addItem(OrderItem.of(order, product, line.getQuantity()));
        }

        Double distanceKm = distanceService.distanceKm(request.address().latitude(), request.address().longitude())
                .orElse(null);
        order.setDistanceKm(distanceKm);
        order.recalculateTotals(cartService.shippingFor(
                order.getItems().stream().mapToLong(OrderItem::getLineTotalPaise).sum()));
        order.setPlacedAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);
        cartService.clear(user);
        log.info("Order {} placed by {} for {} paise", saved.getOrderNumber(), user.getEmail(), saved.getTotalPaise());
        return saved;
    }

    @Transactional
    public void releaseStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQty(product.getStockQty() + item.getQuantity());
            productRepository.save(product);
        }
        log.info("Stock released for order {}", order.getOrderNumber());
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Order getByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> NotFoundException.of("Order", orderNumber));
        order.getItems().size(); // initialise inside the transaction
        return order;
    }

    @Transactional(readOnly = true)
    public Order getForUser(String orderNumber, User user) {
        Order order = getByOrderNumber(orderNumber);
        boolean owner = order.getUser() != null && order.getUser().getId().equals(user.getId());
        if (!owner && !user.getRole().isStaff()) {
            throw NotFoundException.of("Order", orderNumber);
        }
        if (order.isArchived() && !user.getRole().isStaff()) {
            throw NotFoundException.of("Order", orderNumber);
        }
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> myOrders(User user) {
        List<Order> orders = orderRepository.findByUserAndArchivedFalseOrderByIdDesc(user);
        orders.forEach(o -> o.getItems().size());
        return orders;
    }

    @Transactional(readOnly = true)
    public Page<Order> allOrders(OrderStatus status, Pageable pageable) {
        Page<Order> page = status == null
                ? orderRepository.findByArchivedFalseOrderByIdDesc(pageable)
                : orderRepository.findByStatusAndArchivedFalseOrderByIdDesc(status, pageable);
        page.getContent().forEach(this::initialiseForAdmin);
        return page;
    }

    @Transactional(readOnly = true)
    public List<Order> archivedOrders() {
        List<Order> orders = orderRepository.findByArchivedTrueOrderByArchivedAtDesc();
        orders.forEach(o -> o.getItems().size());
        return orders;
    }

    /** Everything the admin orders table reads off an order once the transaction has closed. */
    private void initialiseForAdmin(Order order) {
        order.getItems().size();
        if (order.getDeliveryPartner() != null) {
            order.getDeliveryPartner().getFullName();
        }
    }

    /** The orders an admin has handed to this courier, newest first. */
    @Transactional(readOnly = true)
    public List<Order> partnerOrders(User partner) {
        List<Order> orders = orderRepository.findByDeliveryPartnerAndArchivedFalseOrderByIdDesc(partner);
        orders.forEach(this::initialiseForAdmin);
        return orders;
    }

    // ----------------------------------------------------------------- write

    @Transactional
    public Order updateStatus(String orderNumber, OrderStatus status) {
        Order order = getByOrderNumber(orderNumber);
        if (order.getStatus() == status) {
            return order;
        }
        if (status == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            releaseStock(order);
        }
        order.setStatus(status);
        return orderRepository.save(order);
    }

    /**
     * Is this order's address still inside the delivery area? Always true while no restriction is
     * in force, so shops that never switch the restriction on see no change.
     */
    @Transactional(readOnly = true)
    public boolean isDeliverable(Order order) {
        return order.getShippingAddress() == null
                || deliveryZoneService.isServiceable(order.getShippingAddress().getPincode());
    }

    /**
     * Re-applies the delivery-area rule to an order that is still waiting for payment. An order
     * placed before a PIN code was removed (or before the restriction was switched on) is checked
     * again the moment its customer goes to pay: if we no longer deliver there the order is
     * cancelled, its reserved stock goes back on the shelf, and it can never be paid.
     *
     * <p>Runs in its own transaction and does not throw, so the cancellation is committed even when
     * the caller then reports an error to the customer.</p>
     *
     * @return true if this call cancelled the order
     */
    @Transactional
    public boolean cancelIfOutsideDeliveryArea(String orderNumber) {
        Order order = getByOrderNumber(orderNumber);
        if (!order.isPayable() || isDeliverable(order)) {
            return false;
        }
        // A failed payment already handed its stock back; only unpaid-but-reserved orders hold any.
        boolean holdingStock = order.getStatus() == OrderStatus.CREATED
                || order.getStatus() == OrderStatus.PENDING_PAYMENT;
        if (holdingStock) {
            releaseStock(order);
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Order {} cancelled: PIN code {} is outside the delivery area", orderNumber,
                order.getShippingAddress().getPincode());
        return true;
    }

    /** What the customer is told when {@link #cancelIfOutsideDeliveryArea} cancels their order. */
    public static String outsideAreaMessage(Order order) {
        String pincode = order.getShippingAddress() == null ? "" : order.getShippingAddress().getPincode();
        return "Order " + order.getOrderNumber() + " has been cancelled: we don't have a delivery partner for PIN code "
                + pincode + ", so we can't accept it. Please place a new order with a different delivery address. "
                + "If you have already paid for it, please contact customer care for a refund.";
    }

    /**
     * Admin: hand a paid order to a delivery partner, or pass {@code null} to take it back.
     * Only a paid, still-moving order can be assigned, and only to an account that is genuinely
     * an enabled delivery partner - never to a customer or an admin.
     */
    @Transactional
    public Order assignDeliveryPartner(String orderNumber, Long partnerId) {
        Order order = getByOrderNumber(orderNumber);
        if (order.isArchived()) {
            throw new BusinessException(orderNumber + " is archived");
        }
        if (partnerId == null) {
            order.setDeliveryPartner(null);
            order.setAssignedAt(null);
            log.info("Order {} unassigned from its delivery partner", orderNumber);
            return orderRepository.save(order);
        }
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.PACKED
                && order.getStatus() != OrderStatus.SHIPPED) {
            throw new BusinessException("Only a paid order that has not been delivered can be handed to a delivery"
                    + " partner. " + orderNumber + " is " + order.getStatus());
        }
        User partner = userRepository.findById(partnerId)
                .filter(u -> u.getRole() == Role.DELIVERY_PARTNER && u.isEnabled())
                .orElseThrow(() -> new BusinessException("Choose an active delivery partner"));
        order.setDeliveryPartner(partner);
        order.setAssignedAt(LocalDateTime.now());
        log.info("Order {} assigned to delivery partner {}", orderNumber, partner.getEmail());
        return orderRepository.save(order);
    }

    /**
     * A courier moving their own order along. Deliberately narrower than the admin's
     * {@link #updateStatus}: picked up (PAID/PACKED to SHIPPED) and delivered (SHIPPED to
     * DELIVERED) only, and only on an order that was assigned to this partner.
     */
    @Transactional
    public Order updateStatusAsPartner(String orderNumber, User partner, OrderStatus next) {
        Order order = getByOrderNumber(orderNumber);
        if (order.isArchived() || order.getDeliveryPartner() == null
                || !order.getDeliveryPartner().getId().equals(partner.getId())) {
            throw NotFoundException.of("Order", orderNumber);
        }
        OrderStatus current = order.getStatus();
        boolean pickUp = next == OrderStatus.SHIPPED
                && (current == OrderStatus.PAID || current == OrderStatus.PACKED);
        boolean deliver = next == OrderStatus.DELIVERED && current == OrderStatus.SHIPPED;
        if (!pickUp && !deliver) {
            throw new BusinessException(orderNumber + " is " + current + " and cannot be moved to " + next);
        }
        order.setStatus(next);
        log.info("Delivery partner {} moved order {} to {}", partner.getEmail(), orderNumber, next);
        return orderRepository.save(order);
    }

    /**
     * Super admin: hide an order from the working lists. Stock is returned if the
     * order was still holding any, so archiving never silently loses inventory.
     */
    @Transactional
    public Order archive(String orderNumber) {
        Order order = getByOrderNumber(orderNumber);
        if (order.isArchived()) {
            return order;
        }
        if (order.getStatus() == OrderStatus.CREATED
                || order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            releaseStock(order);
            order.setStatus(OrderStatus.CANCELLED);
        }
        order.setArchived(true);
        order.setArchivedAt(LocalDateTime.now());
        log.info("Order {} archived", orderNumber);
        return orderRepository.save(order);
    }

    @Transactional
    public Order restore(String orderNumber) {
        Order order = getByOrderNumber(orderNumber);
        order.setArchived(false);
        order.setArchivedAt(null);
        return orderRepository.save(order);
    }

    /**
     * Super admin: wipe the order, its lines and its payment rows. Only ever
     * permitted from the archive, so it always takes two deliberate actions.
     */
    @Transactional
    public void deletePermanently(String orderNumber) {
        Order order = getByOrderNumber(orderNumber);
        if (!order.isArchived()) {
            throw new BusinessException("Archive " + orderNumber + " before deleting it permanently");
        }
        orderRepository.delete(order);
        log.warn("Order {} permanently deleted", orderNumber);
    }

    // --------------------------------------------------------------- helpers

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Customers may type "+91 98765 43210", "91-9876543210" or "09876543210"; the order stores only the
     * 10-digit mobile number.
     */
    static String normalizePhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!digits.matches("[6-9][0-9]{9}")) {
            throw new BusinessException("Enter a valid 10 digit Indian mobile number (with or without +91)");
        }
        return digits;
    }

    private ShippingAddress toAddress(AddressRequest a) {
        return new ShippingAddress(a.contactName(), a.contactEmail(), normalizePhone(a.contactPhone()),
                a.addressLine1(), a.addressLine2(), a.city(), a.state(),
                a.pincode() == null ? null : a.pincode().trim(), a.latitude(), a.longitude(), null);
    }

    /**
     * e.g. HP-20260915-K7F2Q9. Doubles as the PhonePe merchantOrderId, so it stays inside
     * the allowed 63 characters and uses only letters, digits, hyphen and underscore.
     */
    private String nextOrderNumber() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder suffix = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String candidate = "HP-" + LocalDate.now().format(DAY) + "-" + suffix;
            if (orderRepository.findByOrderNumber(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique order number");
    }
}
