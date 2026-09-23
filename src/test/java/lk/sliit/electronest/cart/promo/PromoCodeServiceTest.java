package lk.sliit.electronest.cart.promo;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromoCodeServiceTest {
    private final PromoCodeRepository codes = mock(PromoCodeRepository.class);
    private final PromoCodeService service = new PromoCodeService(codes);
    private final BigDecimal subtotal = new BigDecimal("1000.00");
    private final BigDecimal delivery = new BigDecimal("100.00");

    @Test void optionalCodeLeavesFullPrice() {
        assertEquals(new BigDecimal("1100.00"), service.quote(" ", subtotal, delivery).total());
        verifyNoInteractions(codes);
    }
    @Test void normalizedPercentageAppliesOnceAndUsesItemSubtotal() {
        promo();
        for (int i = 0; i < 2; i++) {
            var quote = service.quote(" welcome10 ", subtotal, delivery);
            assertEquals("WELCOME10", quote.code());
            assertEquals(new BigDecimal("100.00"), quote.discount());
            assertEquals(new BigDecimal("1000.00"), quote.total());
        }
    }
    @Test void invalidCodeIsRejected() {
        assertThrows(InvalidPromoCodeException.class, () -> service.quote("UNKNOWN", subtotal, delivery));
    }
    @Test void inactiveCodeIsRejected() {
        promo().setActive(false);
        assertThrows(InvalidPromoCodeException.class, () -> service.quote("WELCOME10", subtotal, delivery));
    }
    @Test void expiredCodeIsRejected() {
        promo().setValidUntil(Instant.now().minusSeconds(1));
        assertThrows(InvalidPromoCodeException.class, () -> service.quote("WELCOME10", subtotal, delivery));
    }
    @Test void futureCodeIsRejected() {
        promo().setValidFrom(Instant.now().plusSeconds(60));
        assertThrows(InvalidPromoCodeException.class, () -> service.quote("WELCOME10", subtotal, delivery));
    }
    @Test void minimumUsesSubtotalAndAcceptsExactBoundary() {
        promo().setMinimumOrderAmount(new BigDecimal("1000.00"));
        assertThrows(InvalidPromoCodeException.class, () -> service.quote("WELCOME10", new BigDecimal("999.99"), delivery));
        assertEquals(new BigDecimal("100.00"), service.quote("WELCOME10", subtotal, delivery).discount());
    }
    @Test void fixedDiscountIsCappedAtGrossTotal() {
        var promo = promo();
        promo.setDiscountType(PromoCode.DiscountType.FIXED);
        promo.setDiscountValue(new BigDecimal("5000.00"));
        var quote = service.quote("WELCOME10", subtotal, delivery);
        assertEquals(new BigDecimal("1100.00"), quote.discount());
        assertEquals(0, quote.total().signum());
    }
    @Test void percentageRoundsToCurrencyPrecision() {
        promo();
        assertEquals(new BigDecimal("1.01"), service.quote("WELCOME10", new BigDecimal("10.05"), BigDecimal.ZERO).discount());
    }
    private PromoCode promo() {
        var promo = new PromoCode();
        promo.setCode("welcome10");
        promo.setDiscountType(PromoCode.DiscountType.PERCENTAGE);
        promo.setDiscountValue(BigDecimal.TEN);
        when(codes.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(promo));
        return promo;
    }
}
