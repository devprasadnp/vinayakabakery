package com.harish.pickles.web.rest;

import com.harish.pickles.domain.OrderStatus;
import com.harish.pickles.dto.DeliveryDtos.DeliveryOrderResponse;
import com.harish.pickles.service.OrderService;
import com.harish.pickles.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A delivery partner's own view: the orders handed to them, and moving those along. */
@RestController
@RequestMapping("/api/delivery")
@PreAuthorize("hasRole('DELIVERY_PARTNER')")
@Tag(name = "Delivery partner")
public class DeliveryPartnerRestController {

    private final OrderService orderService;

    public DeliveryPartnerRestController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    @Operation(summary = "Orders assigned to me, newest first")
    public List<DeliveryOrderResponse> myDeliveries() {
        return orderService.partnerOrders(CurrentUser.require()).stream()
                .map(DeliveryOrderResponse::from).toList();
    }

    @PatchMapping("/orders/{orderNumber}/status")
    @Operation(summary = "Mark an assigned order SHIPPED (picked up) or DELIVERED")
    public DeliveryOrderResponse updateStatus(@PathVariable String orderNumber, @RequestParam OrderStatus status) {
        return DeliveryOrderResponse.from(
                orderService.updateStatusAsPartner(orderNumber, CurrentUser.require(), status));
    }
}
