package com.harish.pickles.web.rest;

import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.Payment;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.Money;
import com.harish.pickles.dto.OrderDtos.CheckoutRequest;
import com.harish.pickles.dto.OrderDtos.OrderResponse;
import com.harish.pickles.dto.OrderDtos.OrderStatusResponse;
import com.harish.pickles.dto.OrderDtos.PaymentInitResponse;
import com.harish.pickles.dto.OrderDtos.PaymentStatusResponse;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.service.OrderService;
import com.harish.pickles.service.PaymentService;
import com.harish.pickles.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders & payment")
public class OrderRestController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderRestController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Turn the cart into an order (stock is reserved here)")
    public ResponseEntity<OrderResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        Order order = orderService.placeOrder(CurrentUser.require(), request);
        return ResponseEntity.status(201).body(OrderResponse.from(order));
    }

    @GetMapping
    @Operation(summary = "My orders")
    public List<OrderResponse> myOrders() {
        return orderService.myOrders(CurrentUser.require()).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{orderNumber}")
    @Operation(summary = "One order")
    public OrderResponse one(@PathVariable String orderNumber) {
        return OrderResponse.from(orderService.getForUser(orderNumber, CurrentUser.require()));
    }

    @PostMapping("/{orderNumber}/pay")
    @Operation(summary = "Start a PhonePe payment - send the browser to the returned redirectUrl. An order for a PIN code we no longer deliver to is cancelled instead.")
    public PaymentInitResponse pay(@PathVariable String orderNumber) {
        User user = CurrentUser.require();
        Order order = orderService.getForUser(orderNumber, user);
        // Cancels (and commits) before we report the problem, so a refused order stays cancelled.
        if (orderService.cancelIfOutsideDeliveryArea(orderNumber)) {
            throw new BusinessException(OrderService.outsideAreaMessage(order));
        }
        return paymentService.initiate(order);
    }

    @GetMapping("/{orderNumber}/status")
    @Operation(summary = "Cheap poll target for the scan-to-pay page — just the order status")
    public OrderStatusResponse status(@PathVariable String orderNumber) {
        Order order = orderService.getForUser(orderNumber, CurrentUser.require());
        return new OrderStatusResponse(order.getOrderNumber(), order.getStatus());
    }

    @GetMapping("/{orderNumber}/payment-status")
    @Operation(summary = "Ask PhonePe what happened and sync our records")
    public PaymentStatusResponse paymentStatus(@PathVariable String orderNumber) {
        User user = CurrentUser.require();
        Order order = orderService.getForUser(orderNumber, user);
        Payment payment = paymentService.reconcile(order.getOrderNumber());
        return new PaymentStatusResponse(order.getOrderNumber(),
                orderService.getByOrderNumber(orderNumber).getStatus(),
                payment.getStatus().name(), payment.getPaymentMode(), payment.getTransactionId(),
                Money.toRupees(payment.getAmountPaise()));
    }
}
