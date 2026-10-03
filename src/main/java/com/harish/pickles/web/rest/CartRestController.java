package com.harish.pickles.web.rest;

import com.harish.pickles.dto.CartDtos.CartItemRequest;
import com.harish.pickles.dto.CartDtos.CartResponse;
import com.harish.pickles.service.CartService;
import com.harish.pickles.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart")
public class CartRestController {

    private final CartService cartService;

    public CartRestController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Current cart with totals and shipping")
    public CartResponse cart() {
        return cartService.summary(CurrentUser.require());
    }

    @PostMapping("/items")
    @Operation(summary = "Add a food to the cart (or set its quantity)")
    public CartResponse add(@Valid @RequestBody CartItemRequest request) {
        return cartService.addOrUpdate(CurrentUser.require(), request.productId(), request.quantity());
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Set the quantity of one line")
    public CartResponse update(@PathVariable Long productId, @Valid @RequestBody CartItemRequest request) {
        return cartService.addOrUpdate(CurrentUser.require(), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove a line")
    public CartResponse remove(@PathVariable Long productId) {
        return cartService.remove(CurrentUser.require(), productId);
    }

    @DeleteMapping
    @Operation(summary = "Empty the cart")
    public CartResponse clear() {
        cartService.clear(CurrentUser.require());
        return cartService.summary(CurrentUser.require());
    }
}
