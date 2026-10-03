package com.harish.pickles.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One PIN code the store delivers to. While the delivery restriction is switched on, an order
 * is accepted only when its address falls on an <em>active</em> row here.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "delivery_pincodes")
public class DeliveryPincode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 6, unique = true)
    private String pincode;

    /** Optional label for the admin's own reference, e.g. "Kakinada town". */
    @Column(name = "area_name", length = 120)
    private String areaName;

    /** Paused pincodes stay listed but no longer accept orders. */
    @Column(nullable = false)
    private boolean active = true;

    public DeliveryPincode(String pincode, String areaName) {
        this.pincode = pincode;
        this.areaName = areaName;
    }
}
