package lk.sliit.electronest.order.controller;

import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.controller.dto.OrderResponse;
import lk.sliit.electronest.order.controller.dto.UpdateOrderStatusRequest;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.service.OrderService;
import org.springframework.http.ResponseEntity;
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

    /** PB-20: order history for a customer. */
    @GetMapping("/customers/{customerId}")
    public ResponseEntity<List<OrderResponse>> historyForCustomer(@PathVariable Long customerId) {
        List<OrderResponse> orders = orderService.getOrderHistoryForCustomer(customerId)
                .stream().map(OrderResponse::from).toList();
        return ResponseEntity.ok(orders);
    }

    /** UC-03 step 1: vendor's order queue. */
    @GetMapping("/vendors/{vendorId}")
    public ResponseEntity<List<OrderResponse>> queueForVendor(@PathVariable Long vendorId) {
        List<OrderResponse> orders = orderService.getOrderQueueForVendor(vendorId)
                .stream().map(OrderResponse::from).toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(OrderResponse.from(orderService.getOrderById(orderId)));
    }

    /** UC-03 main flow: update fulfilment status. */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long orderId,
                                                        @RequestBody UpdateOrderStatusRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails currentUser) {
        User actor = currentUser.getUser();

        Order updated = orderService.updateFulfilmentStatus(
                orderId, actor, request.targetStatus(), request.adminOverride());

        return ResponseEntity.ok(OrderResponse.from(updated));
    }

    /** Customer requests cancellation. */
    @PostMapping("/{orderId}/cancellation-request")
    public ResponseEntity<OrderResponse> requestCancellation(@PathVariable Long orderId) {
        Order updated = orderService.requestCancellation(orderId);
        return ResponseEntity.ok(OrderResponse.from(updated));
    }
}
