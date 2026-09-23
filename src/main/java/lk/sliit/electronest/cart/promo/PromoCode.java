package lk.sliit.electronest.cart.promo;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "promo_codes")
@Getter
@Setter
public class PromoCode {
    public enum DiscountType { PERCENTAGE, FIXED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false)
    private DiscountType discountType;
    @NotNull @Positive @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;
    @Column(nullable = false)
    private boolean active = true;
    private Instant validFrom;
    private Instant validUntil;
    @PositiveOrZero
    @Column(precision = 12, scale = 2)
    private BigDecimal minimumOrderAmount;

    public void setCode(String code) {
        this.code = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    @PrePersist @PreUpdate
    void normalize() {
        setCode(code);
        if (discountType == DiscountType.PERCENTAGE && discountValue != null
                && discountValue.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100");
        }
        if (validFrom != null && validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("Promo expiry must follow its activation date");
        }
    }
}
