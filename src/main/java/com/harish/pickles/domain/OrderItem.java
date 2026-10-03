package com.harish.pickles.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Line on an order. Product details are copied in so history survives price edits. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 160)
    private String productName;

    @Column(nullable = false, length = 64)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickle_type", nullable = false, length = 16)
    private PickleType pickleType;

    @Column(name = "weight_grams", nullable = false)
    private Integer weightGrams;

    @Column(name = "unit_price_paise", nullable = false)
    private Long unitPricePaise;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "line_total_paise", nullable = false)
    private Long lineTotalPaise;

    public static OrderItem of(Order order, Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.order = order;
        item.product = product;
        item.productName = product.getName();
        item.sku = product.getSku();
        item.pickleType = product.getPickleType();
        item.weightGrams = product.getWeightGrams();
        item.unitPricePaise = product.getPricePaise();
        item.quantity = quantity;
        item.lineTotalPaise = product.getPricePaise() * quantity;
        return item;
    }
}
