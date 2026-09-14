package lk.sliit.electronest.order.controller;

import lk.sliit.electronest.common.model.User;
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

/**
 * Security model (per PR review from Kashmira):
 *  - No endpoint below trusts a client-supplied user id for "who am I".
 *    The caller's identity always comes from @AuthenticationPrincipal.
 *  - /my-orders and /vendor/my-queue only ever return the CALLER's own
 *    data - there is no way to pass someone else's id and see their orders.
 *  - /{orderId} and the cancellation endpoint re-check ownership in the
 *    service layer against the specific order being accessed.
 *  - The old /customers/{id} and /vendors/{id} lookups still exist but
 *    are now admin-only (@PreAuthorize), matching the pattern already
 *    used in AdminController, for cases where an admin needs to look
 *    up a specific customer/vendor's orders.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** PB-20: the logged-in customer's own order history. */
    @GetMapping("/my-orders")
    public ResponseEntity<List<OrderResponse>> myOrders(@AuthenticationPrincipal CustomUserDetails currentUser) {
        List<OrderResponse> orders = orderService.getOrderHistoryForCustomer(currentUser.getUser())
                .stream().map(OrderResponse::from).toList();
        return ResponseEntity.ok(orders);
    }

    /** UC-03 step 1: the logged-in vendor's own order queue. */
    @GetMapping("/vendor/my-queue")
    public ResponseEntity<List<OrderResponse>> myVendorQueue(@AuthenticationPrincipal CustomUserDetails currentUser) {
        List<OrderResponse> orders = orderService.getOrderQueueForVendor(currentUser.getUser())
                .stream().map(OrderResponse::from).toList();
        return ResponseEntity.ok(orders);
    }

    /** Admin-only: look up any customer's order history by id. */
    @GetMapping("/customers/{customerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> historyForCustomerAsAdmin(@PathVariable Long customerId) {
        List<OrderResponse> orders = orderService.getOrderHistoryForCustomerId(customerId)
                .stream().map(OrderResponse::from).toList();
        return ResponseEntity.ok(orders);
    }

    /** Admin-only: look up any vendor's order queue by id. */
    @GetMapping("/vendors/{vendorId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> queueForVendorAsAdmin(@PathVariable Long vendorId) {
        List<OrderResponse> orders = orderService.getOrderQueueForVendorId(vendorId)
                .stream().map(OrderResponse::from).toList();
        return ResponseEntity.ok(orders);
    }

    /** Order detail - service layer checks the caller actually owns/administers it. */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId,
                                                    @AuthenticationPrincipal CustomUserDetails currentUser) {
        Order order = orderService.getOrderByIdForViewer(orderId, currentUser.getUser());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    /**
     * UC-03 main flow: update fulfilment status.
     * actor is the authenticated caller - see OrderService for the
     * ownership/role checks this triggers.
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long orderId,
                                                        @RequestBody UpdateOrderStatusRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails currentUser) {
        Order updated = orderService.updateFulfilmentStatus(
                orderId, currentUser.getUser(), request.targetStatus(), request.adminOverride());
        return ResponseEntity.ok(OrderResponse.from(updated));
    }

    /**
     * Customer requests cancellation. Only the customer who placed the
     * order can do this - enforced in OrderService.requestCancellation.
     */
    @PostMapping("/{orderId}/cancellation-request")
    public ResponseEntity<OrderResponse> requestCancellation(@PathVariable Long orderId,
                                                                @AuthenticationPrincipal CustomUserDetails currentUser) {
        Order updated = orderService.requestCancellation(orderId, currentUser.getUser());
        return ResponseEntity.ok(OrderResponse.from(updated));
    }

    /**
     * Creates an order from checkout. The customer is the logged-in
     * caller - Cart/Checkout should call this once payment/checkout is
     * confirmed, passing delivery address + line items.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails currentUser) {
        Order created = orderService.createOrder(request, currentUser.getUser());
        return ResponseEntity.ok(OrderResponse.from(created));
    }
}
