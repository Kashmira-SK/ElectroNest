package lk.sliit.electronest.cart.controller;

import lk.sliit.electronest.cart.dto.CartItemView;
import lk.sliit.electronest.cart.service.CartService;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutTest {
    @Test
    void unavailableItemsReturnToCartAndValidCheckoutCountsUnits() {
        var cart = mock(CartService.class);
        var users = mock(UserRepository.class);
        var orders = mock(OrderService.class);
        var controller = new CartViewController(cart, users, orders, mock(lk.sliit.electronest.cart.promo.PromoCodeService.class));
        User user = new User();
        user.setId(1L);
        var principal = new CustomUserDetails(user);
        when(cart.getCartItemViews(1L)).thenReturn(List.of(item(0)));
        var redirect = new RedirectAttributesModelMap();
        assertEquals("redirect:/cart", controller.checkout(principal, new ExtendedModelMap(), redirect, new org.springframework.mock.web.MockHttpSession()));
        assertTrue(redirect.getFlashAttributes().containsKey("errorMessage"));
        verifyNoInteractions(orders, users);
        when(cart.getCartItemViews(1L)).thenReturn(List.of(item(5)));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        var model = new ExtendedModelMap();
        assertEquals("cart/checkout", controller.checkout(principal, model, redirect, new org.springframework.mock.web.MockHttpSession()));
        assertEquals(3L, model.get("totalItems"));
    }

    private CartItemView item(int stock) {
        return new CartItemView(2L, 3L, "Product", "Brand", null, 3, BigDecimal.TEN, new BigDecimal("30"), stock);
    }
}
