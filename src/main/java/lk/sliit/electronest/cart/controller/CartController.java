package lk.sliit.electronest.cart.controller;

import lk.sliit.electronest.cart.model.CartItem;
import lk.sliit.electronest.cart.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    // Helper method to extract the logged-in user's ID securely
    private Long getAuthenticatedUserId(Principal principal) {
        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }
        return Long.parseLong(principal.getName());
    }

    // READ: Displays the cart page
    @GetMapping
    public String viewCart(Model model, Principal principal) {
        Long userId = getAuthenticatedUserId(principal);
        model.addAttribute("cartItems", cartService.getCartItems(userId));
        model.addAttribute("subtotal", cartService.calculateSubtotal(userId));
        return "cart";
    }

    // CREATE: Adds an item from the catalog (Price is handled in the backend now!)
    @PostMapping("/add")
    public String addToCart(Principal principal, @RequestParam Long productId, @RequestParam Integer quantity) {
        Long userId = getAuthenticatedUserId(principal);
        cartService.addItemToCart(userId, productId, quantity);
        return "redirect:/cart";
    }

    // UPDATE: Changes the quantity of an item
    @PostMapping("/update")
    public String updateQuantity(Principal principal, @RequestParam Long itemId, @RequestParam Integer quantity) {
        Long userId = getAuthenticatedUserId(principal);
        cartService.updateItemQuantity(userId, itemId, quantity);
        return "redirect:/cart";
    }

    // DELETE: Removes an item
    @PostMapping("/remove")
    public String removeItem(Principal principal, @RequestParam Long itemId) {
        Long userId = getAuthenticatedUserId(principal);
        cartService.removeItemFromCart(userId, itemId);
        return "redirect:/cart";
    }

    // CHECKOUT: Validates the cart and prepares the final summary for the order module
    @GetMapping("/checkout/summary")
    public String getCheckoutSummary(Principal principal, Model model) {
        Long userId = getAuthenticatedUserId(principal);
        List<CartItem> items = cartService.getCartItems(userId);

        // Validation: Prevent checkout if cart is empty
        if (items.isEmpty()) {
            return "redirect:/cart?error=empty";
        }

        // Pass data to the final checkout webpage
        model.addAttribute("cartItems", items);
        model.addAttribute("finalTotal", cartService.calculateSubtotal(userId));

        return "checkout-summary";
    }
}