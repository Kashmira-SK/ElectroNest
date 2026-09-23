package lk.sliit.electronest.cart.promo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;

@Service
public class PromoCodeService {
    private final PromoCodeRepository codes;
    public PromoCodeService(PromoCodeRepository codes) { this.codes = codes; }

    public record Quote(String code, BigDecimal subtotal, BigDecimal delivery,
                        BigDecimal discount, BigDecimal total) {}

    @Transactional(readOnly = true)
    public Quote quote(String input, BigDecimal subtotal, BigDecimal delivery) {
        BigDecimal gross = subtotal.add(delivery);
        if (input == null || input.isBlank()) {
            return new Quote(null, subtotal, delivery, BigDecimal.ZERO, gross);
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 50) throw new InvalidPromoCodeException("Promo code is invalid.");
        PromoCode promo = codes.findByCodeIgnoreCase(normalized)
                .orElseThrow(() -> new InvalidPromoCodeException("Promo code is invalid."));
        Instant now = Instant.now();
        if (!promo.isActive()) throw new InvalidPromoCodeException("This promo code is inactive.");
        if (promo.getValidFrom() != null && now.isBefore(promo.getValidFrom()))
            throw new InvalidPromoCodeException("This promo code is not active yet.");
        if (promo.getValidUntil() != null && !now.isBefore(promo.getValidUntil()))
            throw new InvalidPromoCodeException("This promo code has expired.");
        if (promo.getMinimumOrderAmount() != null && subtotal.compareTo(promo.getMinimumOrderAmount()) < 0)
            throw new InvalidPromoCodeException("This promo code requires an item subtotal of at least Rs. "
                    + promo.getMinimumOrderAmount().toPlainString() + ".");
        BigDecimal discount = promo.getDiscountType() == PromoCode.DiscountType.PERCENTAGE
                ? subtotal.multiply(promo.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : promo.getDiscountValue();
        discount = discount.max(BigDecimal.ZERO).min(gross).setScale(2, RoundingMode.HALF_UP);
        return new Quote(promo.getCode(), subtotal, delivery, discount, gross.subtract(discount));
    }
}
