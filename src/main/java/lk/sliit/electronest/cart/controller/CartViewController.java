package lk.sliit.electronest.cart.controller;

import lk.sliit.electronest.cart.dto.CartItemView;
import lk.sliit.electronest.cart.service.CartService;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@PreAuthorize("hasRole('CUSTOMER')")
public class CartViewController {

    private final CartService cartService;
    private final UserRepository userRepository;

    public CartViewController(CartService cartService,
                              UserRepository userRepository) {
        this.cartService = cartService;
        this.userRepository = userRepository;
    }

    @GetMapping("/cart")
    public String cart(@AuthenticationPrincipal CustomUserDetails currentUser,
                       Model model) {
        Long userId = currentUser.getUser().getId();

        model.addAttribute("cartItems", cartService.getCartItemViews(userId));
        model.addAttribute("subtotal", cartService.calculateSubtotal(userId));

        return "cart/cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") Integer quantity) {

        cartService.addItemToCart(
                currentUser.getUser().getId(),
                productId,
                quantity
        );

        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateQuantity(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam Long itemId,
            @RequestParam Integer quantity) {

        cartService.updateItemQuantity(
                currentUser.getUser().getId(),
                itemId,
                quantity
        );

        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeItem(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam Long itemId) {

        cartService.removeItemFromCart(
                currentUser.getUser().getId(),
                itemId
        );

        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String checkout(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        Long userId = currentUser.getUser().getId();
        List<CartItemView> items = cartService.getCartItemViews(userId);

        if (items.isEmpty()) {
            return "redirect:/cart?error=empty";
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        boolean hasSavedDelivery =
                user.getDeliveryAddressLine1() != null &&
                !user.getDeliveryAddressLine1().isBlank();

        model.addAttribute("cartItems", items);
        model.addAttribute("subtotal", cartService.calculateSubtotal(userId));
        model.addAttribute("customer", user);
        model.addAttribute("hasSavedDelivery", hasSavedDelivery);

        return "cart/checkout";
    }

    @PostMapping("/checkout/delivery/save")
    public String saveDeliveryDetails(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam String deliveryName,
            @RequestParam String deliveryPhone,
            @RequestParam String deliveryAddressLine1,
            @RequestParam(required = false) String deliveryAddressLine2,
            @RequestParam String deliveryCity,
            @RequestParam(required = false) String deliveryPostalCode,
            @RequestParam String deliveryCountry) {

        User user = userRepository.findById(currentUser.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        user.setDeliveryName(deliveryName.trim());
        user.setDeliveryPhone(deliveryPhone.trim());
        user.setDeliveryAddressLine1(deliveryAddressLine1.trim());
        user.setDeliveryAddressLine2(cleanOptional(deliveryAddressLine2));
        user.setDeliveryCity(deliveryCity.trim());
        user.setDeliveryPostalCode(cleanOptional(deliveryPostalCode));
        user.setDeliveryCountry(deliveryCountry.trim());

        userRepository.save(user);

        return "redirect:/checkout";
    }

    private String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
