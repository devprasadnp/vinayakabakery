package com.harish.pickles.service;

import com.harish.pickles.config.AppProperties;
import com.harish.pickles.domain.CartItem;
import com.harish.pickles.domain.Product;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.CartDtos.CartLine;
import com.harish.pickles.dto.CartDtos.CartResponse;
import com.harish.pickles.dto.Money;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.exception.NotFoundException;
import com.harish.pickles.repository.CartItemRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductService productService;
    private final AppProperties appProperties;
    private final DeliveryPricingService pricingService;

    public CartService(CartItemRepository cartItemRepository, ProductService productService,
                       AppProperties appProperties, DeliveryPricingService pricingService) {
        this.cartItemRepository = cartItemRepository;
        this.productService = productService;
        this.appProperties = appProperties;
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public List<CartItem> items(User user) {
        return cartItemRepository.findByUserOrderByIdAsc(user);
    }

    @Transactional
    public CartResponse addOrUpdate(User user, Long productId, int quantity) {
        Product product = productService.getById(productId);
        if (!product.isActive() || product.isArchived()) {
            throw new BusinessException(product.getName() + " is no longer available");
        }
        if (quantity > product.getStockQty()) {
            throw new BusinessException("Only " + product.getStockQty() + " jar(s) of "
                    + product.getName() + " left in stock");
        }
        CartItem item = cartItemRepository.findByUserAndProductId(user, productId)
                .orElseGet(() -> new CartItem(user, product, 0));
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return summary(user);
    }

    @Transactional
    public CartResponse increment(User user, Long productId, int delta) {
        CartItem existing = cartItemRepository.findByUserAndProductId(user, productId).orElse(null);
        int target = (existing == null ? 0 : existing.getQuantity()) + delta;
        if (target <= 0) {
            if (existing != null) {
                cartItemRepository.delete(existing);
            }
            return summary(user);
        }
        return addOrUpdate(user, productId, target);
    }

    @Transactional
    public CartResponse remove(User user, Long productId) {
        cartItemRepository.findByUserAndProductId(user, productId)
                .ifPresentOrElse(cartItemRepository::delete,
                        () -> {
                            throw NotFoundException.of("Cart item", productId);
                        });
        return summary(user);
    }

    @Transactional
    public void clear(User user) {
        cartItemRepository.deleteByUser(user);
    }

    @Transactional(readOnly = true)
    public CartResponse summary(User user) {
        List<CartItem> items = items(user);
        List<CartLine> lines = items.stream()
                .map(i -> new CartLine(
                        i.getId(),
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getProduct().getSlug(),
                        i.getProduct().getPickleType().getLabel(),
                        i.getProduct().getWeightGrams(),
                        i.getProduct().getDisplayImageUrl(),
                        Money.toRupees(i.getProduct().getPricePaise()),
                        i.getQuantity(),
                        Money.toRupees(i.getLineTotalPaise()),
                        i.getProduct().getStockQty()))
                .toList();

        long subtotal = items.stream().mapToLong(CartItem::getLineTotalPaise).sum();
        long shipping = shippingFor(subtotal);
        int count = items.stream().mapToInt(CartItem::getQuantity).sum();

        return new CartResponse(lines, count,
                Money.toRupees(subtotal), Money.toRupees(shipping), Money.toRupees(subtotal + shipping),
                Money.toRupees(appProperties.getShipping().getFreeAbovePaise()),
                subtotal > 0 && shipping == 0);
    }

    /** Default-location courier fee (no pin yet), waived once the basket crosses the free-shipping threshold. */
    public long shippingFor(long subtotalPaise) {
        return pricingService.shippingFor(subtotalPaise, null);
    }

    /** Courier fee for a delivery {@code distanceKm} from the store; see {@link DeliveryPricingService}. */
    public long shippingFor(long subtotalPaise, Double distanceKm) {
        return pricingService.shippingFor(subtotalPaise, distanceKm);
    }
}
