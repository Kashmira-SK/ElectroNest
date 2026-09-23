package lk.sliit.electronest.cart.controller;

import jakarta.servlet.http.HttpSession;
import lk.sliit.electronest.cart.promo.InvalidPromoCodeException;
import lk.sliit.electronest.cart.promo.PromoCodeService;
import lk.sliit.electronest.cart.service.CartService;
import lk.sliit.electronest.common.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;

@Controller
@PreAuthorize("hasRole('CUSTOMER')")
public class CheckoutPromoController {
    private final CartService carts;
    private final PromoCodeService promos;

    public CheckoutPromoController(CartService carts, PromoCodeService promos) {
        this.carts = carts;
        this.promos = promos;
    }

    // User-specific even if an account changes within the same browser session.
    static String sessionKey(Long userId) { return "checkoutPromo:" + userId; }

    public record Result(PromoCodeService.Quote quote, String error) {}

    @PostMapping("/checkout/promo")
    public Object update(@RequestParam(defaultValue = "") String code,
                         @RequestParam(defaultValue = "apply") String action,
                         @RequestHeader(value = "Accept", defaultValue = "") String accept,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         HttpSession session, RedirectAttributes redirect) {
        Long userId = currentUser.getUser().getId();
        BigDecimal subtotal = carts.calculateSubtotal(userId);
        String error = null;
        PromoCodeService.Quote quote;
        try {
            if (!action.equals("remove") && code.isBlank()) {
                throw new InvalidPromoCodeException("Enter a promo code to apply.");
            }
            quote = promos.quote(action.equals("remove") ? null : code, subtotal, BigDecimal.ZERO);
        } catch (InvalidPromoCodeException ex) {
            error = ex.getMessage() + " You can continue at full price.";
            quote = promos.quote(null, subtotal, BigDecimal.ZERO);
        }
        if (quote.code() == null) session.removeAttribute(sessionKey(userId));
        else session.setAttribute(sessionKey(userId), quote.code());
        if (accept.contains("application/json")) {
            return ResponseEntity.status(error == null ? 200 : 400).body(new Result(quote, error));
        }
        if (error != null) redirect.addFlashAttribute("promoError", error);
        return "redirect:/checkout";
    }
}
