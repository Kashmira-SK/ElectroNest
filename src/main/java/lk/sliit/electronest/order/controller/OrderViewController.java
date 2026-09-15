package lk.sliit.electronest.order.controller;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.controller.dto.OrderItemView;
import lk.sliit.electronest.order.controller.dto.OrderViewData;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.model.OrderLineItem;
import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.order.service.OrderService;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
public class OrderViewController {

    private final OrderService orderService;
    private final VendorService vendorService;
    private final ProductService productService;

    public OrderViewController(
            OrderService orderService,
            VendorService vendorService,
            ProductService productService) {
        this.orderService = orderService;
        this.vendorService = vendorService;
        this.productService = productService;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/orders")
    public String customerOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        List<OrderViewData> orders = orderService
                .getOrderHistoryForCustomer(currentUser.getUser())
                .stream()
                .map(order -> toView(order, null))
                .toList();

        model.addAttribute("orders", orders);

        return "order/customer-orders";
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/orders/{id}/cancel")
    public String requestCancellation(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.requestCancellation(
                    id,
                    currentUser.getUser()
            );

            String message = order.isCancellationRequested()
                    ? "Cancellation requested for order #" + order.getId() + "."
                    : "Order #" + order.getId() + " was cancelled.";
            redirectAttributes.addFlashAttribute("successMessage", message);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/orders";
    }

    @PreAuthorize("hasRole('VENDOR')")
    @GetMapping("/vendor/orders")
    public String vendorOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes) {

        Vendor vendor = approvedVendor(currentUser, redirectAttributes);
        if (vendor == null) {
            return "redirect:/vendor/status";
        }

        List<OrderViewData> orders = orderService
                .getOrderQueueForVendor(currentUser.getUser())
                .stream()
                .map(order -> toView(order, currentUser.getUser().getId()))
                .toList();

        model.addAttribute("vendor", vendor);
        model.addAttribute("orders", orders);

        return "order/vendor-orders";
    }

    @PreAuthorize("hasRole('VENDOR')")
    @PostMapping("/vendor/orders/{id}/status")
    public String updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        if (approvedVendor(currentUser, redirectAttributes) == null) {
            return "redirect:/vendor/status";
        }

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

    private Vendor approvedVendor(
            CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {
        try {
            Vendor vendor = vendorService.getVendorForUser(
                    currentUser.getUser().getId()
            );

            if (vendor.getStatus() != VendorStatus.APPROVED) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "Vendor orders are available after your application is approved."
                );
                return null;
            }

            return vendor;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return null;
        }
    }

    private OrderViewData toView(Order order, Long vendorUserId) {
        List<OrderItemView> items = order.getLineItems().stream()
                .filter(item -> vendorUserId == null || item.belongsToVendor(vendorUserId))
                .map(this::toItemView)
                .toList();

        BigDecimal displayTotal = items.stream()
                .map(OrderItemView::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrderViewData(order, items, displayTotal);
    }

    private OrderItemView toItemView(OrderLineItem item) {
        String productName;

        try {
            Product product = productService.getProductById(item.getProductId());
            productName = product.getName();
        } catch (RuntimeException ex) {
            productName = "Product #" + item.getProductId();
        }

        BigDecimal lineTotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));

        return new OrderItemView(
                item.getProductId(),
                productName,
                item.getQuantity(),
                item.getUnitPrice(),
                lineTotal
        );
    }
}
