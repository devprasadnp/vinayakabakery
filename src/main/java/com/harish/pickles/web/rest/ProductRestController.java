package com.harish.pickles.web.rest;

import com.harish.pickles.domain.PickleType;
import com.harish.pickles.dto.ProductDtos.ProductResponse;
import com.harish.pickles.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated catalogue browsing - what the storefront itself calls. */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Products")
public class ProductRestController {

    private final ProductService productService;

    public ProductRestController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Browse live foods, optionally filtered by dietary type, category or a search term")
    public Page<ProductResponse> browse(@RequestParam(required = false) PickleType type,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) String sort,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "12") int size) {
        return productService.browse(type, categoryId, q,
                        PageRequest.of(page, Math.min(size, 100), ProductService.sortFor(sort)))
                .map(ProductResponse::from);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "A single live food by its slug")
    public ProductResponse bySlug(@PathVariable String slug) {
        return ProductResponse.from(productService.getBySlug(slug));
    }
}
