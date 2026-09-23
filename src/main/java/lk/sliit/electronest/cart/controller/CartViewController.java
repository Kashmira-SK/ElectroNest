package lk.sliit.electronest.cart.controller;

import lk.sliit.electronest.cart.dto.CartItemView;
import lk.sliit.electronest.cart.dto.DeliveryDetailsForm;
import lk.sliit.electronest.cart.service.CartService;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.controller.dto.CreateOrderRequest;
import lk.sliit.electronest.order.controller.dto.OrderLineItemRequest;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import lk.sliit.electronest.cart.promo.PromoCodeService;
import lk.sliit.electronest.cart.promo.InvalidPromoCodeException;

import java.util.List;
import java.util.Map;

@Controller
@PreAuthorize("hasRole('CUSTOMER')")
public class CartViewController {

    private final PromoCodeService promos;
    private final CartService cartService;
    private final UserRepository userRepository;
    private final OrderService orderService;

    public CartViewController(
            CartService cartService,
            UserRepository userRepository,
            OrderService orderService, PromoCodeService promos) {
        this.promos = promos;
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
        model.addAttribute("totalItems", cartService.getCartSummary(userId).totalItems());

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
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Cart quantity updated."
            );
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return "redirect:/cart";
    }

    @PostMapping("/cart/update-quantity")
    @ResponseBody
    public ResponseEntity<?> updateQuantityAjax(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam Long itemId,
            @RequestParam Integer quantity) {

        Long userId = currentUser.getUser().getId();

        try {
            cartService.updateItemQuantity(userId, itemId, quantity);

            CartItemView item = cartService.getCartItemViews(userId)
                    .stream()
                    .filter(view -> view.itemId().equals(itemId))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalStateException("Updated cart item not found"));

            var summary = cartService.getCartSummary(userId);

            return ResponseEntity.ok(Map.of(
                    "quantity", item.quantity(),
                    "lineTotal", item.lineTotal(),
                    "subtotal", summary.subtotal(),
                    "totalItems", summary.totalItems(),
                    "stockQuantity", item.stockQuantity()
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage()));
        }
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
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Product removed from your cart."
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
            Model model,
            RedirectAttributes redirectAttributes, HttpSession session) {

        Long userId = currentUser.getUser().getId();
        List<CartItemView> items = cartService.getCartItemViews(userId);

        if (items.isEmpty()) {
            return "redirect:/cart";
        }
        if (items.stream().anyMatch(item -> item.quantity() > item.stockQuantity())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Some cart items are unavailable or exceed current stock. Adjust quantities or remove them before checkout.");
            return "redirect:/cart";
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException("User not found"));

        model.addAttribute("cartItems", items);
        model.addAttribute("totalItems", items.stream().mapToLong(CartItemView::quantity).sum());
        BigDecimal subtotal = cartService.calculateSubtotal(userId);
        model.addAttribute("subtotal", subtotal);
        PromoCodeService.Quote quote;
        try {
            quote = promos.quote((String) session.getAttribute(CheckoutPromoController.sessionKey(userId)),
                    subtotal, BigDecimal.ZERO);
        } catch (InvalidPromoCodeException ex) {
            session.removeAttribute(CheckoutPromoController.sessionKey(userId));
            model.addAttribute("promoError", ex.getMessage() + " You can continue at full price.");
            quote = promos.quote(null, subtotal, BigDecimal.ZERO);
        }
        model.addAttribute("checkoutQuote", quote);
        model.addAttribute("customer", user);
        model.addAttribute("hasSavedDelivery", hasSavedDelivery(user));
        model.addAttribute(
                "editingDelivery",
                model.containsAttribute(
                        "org.springframework.validation.BindingResult.deliveryDetailsForm"
                )
        );

        if (!model.containsAttribute("deliveryDetailsForm")) {
            model.addAttribute("deliveryDetailsForm", deliveryDetailsFrom(user));
        }

        return "cart/checkout";
    }

    @PostMapping("/checkout/delivery/save")
    public String saveDeliveryDetails(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @ModelAttribute("deliveryDetailsForm") DeliveryDetailsForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.deliveryDetailsForm",
                    bindingResult
            );
            redirectAttributes.addFlashAttribute("deliveryDetailsForm", form);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    bindingResult.getAllErrors().getFirst().getDefaultMessage()
            );
            return "redirect:/checkout";
        }

        User user = userRepository.findById(
                        currentUser.getUser().getId())
                .orElseThrow(() ->
                        new IllegalStateException("User not found"));

        user.setDeliveryName(form.getDeliveryName().trim());
        user.setDeliveryPhone(form.getDeliveryPhone().trim());
        user.setDeliveryAddressLine1(form.getDeliveryAddressLine1().trim());
        user.setDeliveryAddressLine2(cleanOptional(form.getDeliveryAddressLine2()));
        user.setDeliveryCity(form.getDeliveryCity().trim());
        user.setDeliveryPostalCode(cleanOptional(form.getDeliveryPostalCode()));
        user.setDeliveryCountry(form.getDeliveryCountry().trim());

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
            RedirectAttributes redirectAttributes, HttpSession session) {

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
                orderItems,
                (String) session.getAttribute(CheckoutPromoController.sessionKey(userId))
        );

        try {
            Order order = orderService.createOrder(
                    request,
                    currentUser.getUser()
            );

            session.removeAttribute(CheckoutPromoController.sessionKey(userId));
            return "redirect:/payment?orderId=" + order.getId();

        } catch (InvalidPromoCodeException ex) {
            session.removeAttribute(CheckoutPromoController.sessionKey(userId));
            redirectAttributes.addFlashAttribute("promoError", ex.getMessage()
                    + " Review the full-price total and continue, or apply another code.");
            return "redirect:/checkout";
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

    private DeliveryDetailsForm deliveryDetailsFrom(User user) {
        DeliveryDetailsForm form = new DeliveryDetailsForm();
        form.setDeliveryName(hasText(user.getDeliveryName())
                ? user.getDeliveryName()
                : user.getFullName());
        form.setDeliveryPhone(hasText(user.getDeliveryPhone())
                ? user.getDeliveryPhone()
                : user.getContactNumber());
        form.setDeliveryAddressLine1(user.getDeliveryAddressLine1());
        form.setDeliveryAddressLine2(user.getDeliveryAddressLine2());
        form.setDeliveryCity(user.getDeliveryCity());
        form.setDeliveryPostalCode(user.getDeliveryPostalCode());
        form.setDeliveryCountry(user.getDeliveryCountry());
        return form;
    }
}
