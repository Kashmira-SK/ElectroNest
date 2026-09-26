package lk.sliit.electronest.order.controller;

import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.controller.dto.CreateOrderRequest;
import lk.sliit.electronest.order.controller.dto.OrderResponse;
import lk.sliit.electronest.order.controller.dto.UpdateOrderStatusRequest;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<OrderResponse>> myOrders(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                orderService.getOrderHistoryForCustomer(currentUser.getUser())
                        .stream()
                        .map(OrderResponse::from)
                        .toList()
        );
    }

    @GetMapping("/vendor/my-queue")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<List<OrderResponse>> myVendorQueue(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                orderService.getOrderQueueForVendor(currentUser.getUser())
                        .stream()
                        .map(order -> OrderResponse.from(order, currentUser.getUser().getId()))
                        .toList()
        );
    }

    @GetMapping("/customers/{customerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> customerOrders(
            @PathVariable Long customerId) {

        return ResponseEntity.ok(
                orderService.getOrderHistoryForCustomerId(customerId)
                        .stream()
                        .map(OrderResponse::from)
                        .toList()
        );
    }

    @GetMapping("/vendors/{vendorId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> vendorOrders(
            @PathVariable Long vendorId) {

        return ResponseEntity.ok(
                orderService.getOrderQueueForVendorId(vendorId)
                        .stream()
                        .map(OrderResponse::from)
                        .toList()
        );
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                OrderResponse.from(
                        orderService.getOrderByIdForViewer(
                                orderId,
                                currentUser.getUser()
                        ), currentUser.getUser().getRole() == lk.sliit.electronest.common.model.Role.VENDOR
                                ? currentUser.getUser().getId() : null
                )
        );
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('VENDOR','ADMIN')")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long orderId,
            @RequestBody UpdateOrderStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Order updated = orderService.updateFulfilmentStatus(
                orderId,
                currentUser.getUser(),
                request.targetStatus(),
                request.adminOverride()
        );

        return ResponseEntity.ok(OrderResponse.from(updated, currentUser.getUser().getRole() == lk.sliit.electronest.common.model.Role.VENDOR
                ? currentUser.getUser().getId() : null));
    }

    @PostMapping("/{orderId}/cancellation-request")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> requestCancellation(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                OrderResponse.from(
                        orderService.requestCancellation(
                                orderId,
                                currentUser.getUser()
                        )
                )
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                OrderResponse.from(
                        orderService.createOrder(
                                request,
                                currentUser.getUser()
                        )
                )
        );
    }
}
