package lk.sliit.electronest.order.service;

import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * All the business rules for UC-03 (Update Order Fulfilment Status)
 * live here — this is the class to know inside-out for the viva.
 *
 * Rules implemented:
 *  1. Only the vendor who owns at least one line item, or an admin,
 *     may change an order's status.
 *  2. Status can only move along the legal path defined in
 *     OrderStatus (see canTransitionTo) — e.g. can't jump straight
 *     from PENDING to DELIVERED.
 *  3. A DELIVERED order can't be reverted unless an admin explicitly
 *     overrides it.
 *  4. Every change updates the "updatedAt" timestamp so there's an
 *     audit trail of when the order last moved.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> getOrderHistoryForCustomer(Long customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public List<Order> getOrderQueueForVendor(Long vendorId) {
        return orderRepository.findByVendorId(vendorId);
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
    }

    /**
     * Core UC-03 operation: main flow steps 3-7.
     *
     * @param orderId      the order being updated
     * @param actor        who is making the change (vendor or admin)
     * @param targetStatus the requested next status
     * @param adminOverride true only when an admin is explicitly
     *                       reverting a DELIVERED order
     */
    @Transactional
    public Order updateFulfilmentStatus(Long orderId, User actor, OrderStatus targetStatus,
                                         boolean adminOverride) {
        Order order = getOrderById(orderId);

        assertActorMayModify(order, actor);

        OrderStatus currentStatus = order.getStatus();

        boolean isOrdinaryLegalMove = currentStatus.canTransitionTo(targetStatus);
        boolean isAdminOverridingDelivered = currentStatus == OrderStatus.DELIVERED
                && actor.getRole() == Role.ADMIN
                && adminOverride;

        if (!isOrdinaryLegalMove && !isAdminOverridingDelivered) {
            throw new IllegalStateException(
                    "Cannot move order " + orderId + " from " + currentStatus + " to " + targetStatus);
        }

        order.setStatus(targetStatus);
        order.setCancellationRequested(false);
        order.touch();

        return orderRepository.save(order);
        // TODO: once the notification module exists, publish an event
        // here so the customer gets notified (UC-03 step 7).
    }

    /**
     * Customer-initiated cancellation. If the order hasn't started
     * processing yet, cancel immediately; otherwise just flag it for
     * vendor/admin review (UC-03 alt-flow 7a).
     */
    @Transactional
    public Order requestCancellation(Long orderId) {
        Order order = getOrderById(orderId);

        if (order.getStatus().isTerminal()) {
            throw new IllegalStateException("Order " + orderId + " is already " + order.getStatus());
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CANCELLED);
        } else {
            order.setCancellationRequested(true);
        }
        order.touch();

        return orderRepository.save(order);
    }

    private void assertActorMayModify(Order order, User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (actor.getRole() == Role.VENDOR) {
            boolean ownsAtLeastOneItem = order.getLineItems().stream()
                    .anyMatch(item -> item.belongsToVendor(actor.getId()));
            if (ownsAtLeastOneItem) {
                return;
            }
        }
        throw new SecurityException(
                "User " + actor.getId() + " is not permitted to modify order " + order.getId());
    }
}
