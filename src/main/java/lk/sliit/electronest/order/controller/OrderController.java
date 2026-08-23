package lk.sliit.electronest.order.controller;

import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.order.controller.dto.OrderResponse;
import lk.sliit.electronest.order.controller.dto.UpdateOrderStatusRequest;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * NOTE: assumes a UserRepository already exists in
 * common.repository (Navodya's Admin/User module) with a standard
 * findById(Long). Adjust the import/package if hers differs.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    public OrderController(OrderService orderService, UserRepository userRepository) {
        this.orderService = orderService;
        this.userRepository = userRepository;
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
                                                        @RequestBody UpdateOrderStatusRequest request) {
        User actor = userRepository.findById(request.actorId())
                .orElseThrow(() -> new NoSuchElementException("User not found: " + request.actorId()));

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
