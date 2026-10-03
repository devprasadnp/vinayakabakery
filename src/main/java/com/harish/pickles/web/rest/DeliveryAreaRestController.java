package com.harish.pickles.web.rest;

import com.harish.pickles.service.DeliveryZoneService;
import com.harish.pickles.service.DeliveryZoneService.Availability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public: lets the checkout page tell a customer, before they pay, whether we deliver to their PIN code. */
@RestController
@RequestMapping("/api/delivery-areas")
@Tag(name = "Delivery areas")
public class DeliveryAreaRestController {

    private final DeliveryZoneService deliveryZoneService;

    public DeliveryAreaRestController(DeliveryZoneService deliveryZoneService) {
        this.deliveryZoneService = deliveryZoneService;
    }

    @GetMapping("/check")
    @Operation(summary = "Do we deliver to this PIN code?")
    public Availability check(@RequestParam String pincode) {
        return deliveryZoneService.check(pincode);
    }
}
