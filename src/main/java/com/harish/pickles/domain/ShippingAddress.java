package com.harish.pickles.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ShippingAddress {

    @NotBlank
    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @NotBlank
    @Column(name = "contact_email", nullable = false, length = 160)
    private String contactEmail;

    @NotBlank
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10 digit Indian mobile number")
    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @NotBlank
    @Column(name = "address_line1", nullable = false, length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @NotBlank
    @Column(nullable = false, length = 80)
    private String city;

    @NotBlank
    @Column(nullable = false, length = 80)
    private String state;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "Enter a valid 6 digit PIN code")
    @Column(nullable = false, length = 10)
    private String pincode;

    /** Dropped pin from the checkout map, when Google Maps is configured. Null otherwise. */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /** Google's readable address for the dropped pin, as a second reference for the delivery partner. */
    @Column(name = "pin_address", length = 300)
    private String pinAddress;

    public ShippingAddress(String contactName, String contactEmail, String contactPhone, String addressLine1,
                           String addressLine2, String city, String state, String pincode) {
        this(contactName, contactEmail, contactPhone, addressLine1, addressLine2, city, state, pincode, null, null, null);
    }

    public boolean hasPin() {
        return latitude != null && longitude != null;
    }

    public String getSingleLine() {
        StringBuilder sb = new StringBuilder(addressLine1);
        if (addressLine2 != null && !addressLine2.isBlank()) {
            sb.append(", ").append(addressLine2);
        }
        return sb.append(", ").append(city).append(", ").append(state).append(" - ").append(pincode).toString();
    }
}
