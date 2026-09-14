package lk.sliit.electronest.cart.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CartViewController {

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/cart")
    public String cart() {
        return "cart/cart";
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/checkout")
    public String checkout() {
        return "cart/checkout";
    }
}
