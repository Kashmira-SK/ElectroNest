package lk.sliit.electronest.cart.controller;

import lk.sliit.electronest.cart.dto.CartSummaryResponse;
import lk.sliit.electronest.cart.service.CartService;
import lk.sliit.electronest.common.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartSummaryResponse getCart(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return cartService.getCartSummary(
                currentUser.getUser().getId()
        );
    }

    @PostMapping("/items")
    public CartSummaryResponse addItem(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") Integer quantity) {

        Long userId = currentUser.getUser().getId();

        cartService.addItemToCart(userId, productId, quantity);

        return cartService.getCartSummary(userId);
    }

    @PatchMapping("/items/{itemId}")
    public CartSummaryResponse updateItem(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long itemId,
            @RequestParam Integer quantity) {

        Long userId = currentUser.getUser().getId();

        cartService.updateItemQuantity(userId, itemId, quantity);

        return cartService.getCartSummary(userId);
    }

    @DeleteMapping("/items/{itemId}")
    public CartSummaryResponse removeItem(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long itemId) {

        Long userId = currentUser.getUser().getId();

        cartService.removeItemFromCart(userId, itemId);

        return cartService.getCartSummary(userId);
    }

    @GetMapping("/checkout-summary")
    public CartSummaryResponse checkoutSummary(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        CartSummaryResponse summary = cartService.getCartSummary(
                currentUser.getUser().getId()
        );

        if (summary.items().isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        return summary;
    }
}
