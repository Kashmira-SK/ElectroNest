package lk.sliit.electronest.order.controller;

import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.order.service.OrderService;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OrderViewController {

    private final OrderService orderService;
    private final VendorService vendorService;

    public OrderViewController(
            OrderService orderService,
            VendorService vendorService) {
        this.orderService = orderService;
        this.vendorService = vendorService;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/orders")
    public String customerOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        model.addAttribute(
                "orders",
                orderService.getOrderHistoryForCustomer(
                        currentUser.getUser().getId()
                )
        );

        return "order/customer-orders";
    }

    @PreAuthorize("hasRole('VENDOR')")
    @GetMapping("/vendor/orders")
    public String vendorOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        Vendor vendor = vendorService.getVendorForUser(
                currentUser.getUser().getId()
        );

        model.addAttribute(
                "orders",
                orderService.getOrderQueueForVendor(vendor.getId())
        );

        return "order/vendor-orders";
    }

    @PreAuthorize("hasRole('VENDOR')")
    @PostMapping("/vendor/orders/{id}/status")
    public String updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        try {
            Order order = orderService.updateFulfilmentStatus(
                    id,
                    currentUser.getUser(),
                    status,
                    false
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Order #" + order.getId() + " updated to " + order.getStatus() + "."
            );
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return "redirect:/vendor/orders";
    }
}
