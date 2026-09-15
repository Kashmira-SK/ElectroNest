package lk.sliit.electronest.cart.controller;

import lk.sliit.electronest.cart.dto.CartItemView;
import lk.sliit.electronest.cart.service.CartService;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.controller.dto.CreateOrderRequest;
import lk.sliit.electronest.order.controller.dto.OrderLineItemRequest;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.service.OrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@PreAuthorize("hasRole('CUSTOMER')")
public class CartViewController {

    private final CartService cartService;
    private final UserRepository userRepository;
    private final OrderService orderService;

    public CartViewController(
            CartService cartService,
            UserRepository userRepository,
            OrderService orderService) {
        this.cartService = cartService;
        this.userRepository = userRepository;
        this.orderService = orderService;
    }

    @GetMapping("/cart")
    public String cart(
            @AuthenticationPrincipal CustomUserDetails currentUser,
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
            @RequestParam(defaultValue = "1") Integer quantity,
            RedirectAttributes redirectAttributes) {

        try {
            cartService.addItemToCart(
                    currentUser.getUser().getId(),
                    productId,
                    quantity
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Product added to your cart."
            );
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateQuantity(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam Long itemId,
            @RequestParam Integer quantity,
            RedirectAttributes redirectAttributes) {

        try {
            cartService.updateItemQuantity(
                    currentUser.getUser().getId(),
                    itemId,
                    quantity
            );
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeItem(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam Long itemId,
            RedirectAttributes redirectAttributes) {

        try {
            cartService.removeItemFromCart(
                    currentUser.getUser().getId(),
                    itemId
            );
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String checkout(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        Long userId = currentUser.getUser().getId();
        List<CartItemView> items = cartService.getCartItemViews(userId);

        if (items.isEmpty()) {
            return "redirect:/cart";
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException("User not found"));

        model.addAttribute("cartItems", items);
        model.addAttribute("subtotal", cartService.calculateSubtotal(userId));
        model.addAttribute("customer", user);
        model.addAttribute("hasSavedDelivery", hasSavedDelivery(user));

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
            @RequestParam String deliveryCountry,
            RedirectAttributes redirectAttributes) {

        User user = userRepository.findById(
                        currentUser.getUser().getId())
                .orElseThrow(() ->
                        new IllegalStateException("User not found"));

        user.setDeliveryName(deliveryName.trim());
        user.setDeliveryPhone(deliveryPhone.trim());
        user.setDeliveryAddressLine1(deliveryAddressLine1.trim());
        user.setDeliveryAddressLine2(cleanOptional(deliveryAddressLine2));
        user.setDeliveryCity(deliveryCity.trim());
        user.setDeliveryPostalCode(cleanOptional(deliveryPostalCode));
        user.setDeliveryCountry(deliveryCountry.trim());

        userRepository.save(user);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Delivery details saved."
        );

        return "redirect:/checkout";
    }

    @PostMapping("/checkout/order")
    public String createOrder(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        Long userId = currentUser.getUser().getId();

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException("User not found"));

        List<CartItemView> cartItems =
                cartService.getCartItemViews(userId);

        if (cartItems.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Your cart is empty."
            );
            return "redirect:/cart";
        }

        if (!hasSavedDelivery(user)) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Save your delivery details before continuing."
            );
            return "redirect:/checkout";
        }

        List<OrderLineItemRequest> orderItems = cartItems.stream()
                .map(item -> new OrderLineItemRequest(
                        item.productId(),
                        item.quantity()
                ))
                .toList();

        CreateOrderRequest request = new CreateOrderRequest(
                user.getDeliveryName(),
                user.getDeliveryPhone(),
                user.getDeliveryAddressLine1(),
                user.getDeliveryAddressLine2(),
                user.getDeliveryCity(),
                user.getDeliveryPostalCode(),
                user.getDeliveryCountry(),
                orderItems
        );

        try {
            Order order = orderService.createOrder(
                    request,
                    currentUser.getUser()
            );

            return "redirect:/payment?orderId=" + order.getId();

        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
            return "redirect:/checkout";
        }
    }

    private boolean hasSavedDelivery(User user) {
        return hasText(user.getDeliveryName())
                && hasText(user.getDeliveryPhone())
                && hasText(user.getDeliveryAddressLine1())
                && hasText(user.getDeliveryCity())
                && hasText(user.getDeliveryCountry());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
