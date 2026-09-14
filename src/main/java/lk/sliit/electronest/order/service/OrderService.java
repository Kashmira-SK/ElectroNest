package lk.sliit.electronest.order.service;

import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.order.controller.dto.CreateOrderRequest;
import lk.sliit.electronest.order.controller.dto.OrderLineItemRequest;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.model.OrderLineItem;
import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.order.model.PaymentStatus;
import lk.sliit.electronest.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;


@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }


    public List<Order> getOrderHistoryForCustomer(User customer) {
        return orderRepository.findByCustomer_Id(customer.getId());
    }


    public List<Order> getOrderHistoryForCustomerId(Long customerId) {
        return orderRepository.findByCustomer_Id(customerId);
    }


    public List<Order> getOrderQueueForVendor(User vendor) {
        return orderRepository.findByVendorId(vendor.getId());
    }

    /** Admin-only escape hatch, same reasoning as getOrderHistoryForCustomerId. */
    public List<Order> getOrderQueueForVendorId(Long vendorId) {
        return orderRepository.findByVendorId(vendorId);
    }


    public Order getOrderByIdForViewer(Long orderId, User viewer) {
        Order order = getOrderById(orderId);
        assertActorMayView(order, viewer);
        return order;
    }


    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
    }


    @Transactional
    public Order updateFulfilmentStatus(Long orderId, User actor, OrderStatus targetStatus,
                                         boolean adminOverride) {
        Order order = getOrderById(orderId);

        assertActorMayModifyStatus(order, actor);

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
        order.setUpdatedAt(LocalDateTime.now());

        return orderRepository.save(order);
        // TODO: once the notification module exists, publish an event
        // here so the customer gets notified (UC-03 step 7).
    }


    @Transactional
    public Order requestCancellation(Long orderId, User customer) {
        Order order = getOrderById(orderId);

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new SecurityException(
                    "Customer " + customer.getId() + " does not own order " + orderId);
        }

        if (order.getStatus().isTerminal()) {
            throw new IllegalStateException("Order " + orderId + " is already " + order.getStatus());
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CANCELLED);
        } else {
            order.setCancellationRequested(true);
        }
        order.setUpdatedAt(LocalDateTime.now());

        return orderRepository.save(order);
    }

    /**
     * Creates an order from checkout. The customer is the authenticated
     * caller - never a value passed in the request. Vendors on each
     * line item are looked up by id to make sure they actually exist
     * and really do have the VENDOR role, so a bad/forged vendorId
     * fails loudly here instead of silently corrupting the order.
     *
     * TODO: unitPrice is currently trusted from the client (see
     * OrderLineItemRequest) until the Catalog module exposes a price
     * lookup this service can call.
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request, User customer) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("An order must have at least one item");
        }

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING_PAYMENT);
        order.setAddressLine1(request.addressLine1());
        order.setCity(request.city());
        order.setPostalCode(request.postalCode());
        order.setCountry(request.country());
        order.setCancellationRequested(false);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        for (OrderLineItemRequest itemRequest : request.items()) {
            User vendor = userRepository.findById(itemRequest.vendorId())
                    .orElseThrow(() -> new NoSuchElementException("Vendor not found: " + itemRequest.vendorId()));

            if (vendor.getRole() != Role.VENDOR) {
                throw new IllegalArgumentException("User " + vendor.getId() + " is not a vendor");
            }

            OrderLineItem lineItem = new OrderLineItem();
            lineItem.setProductId(itemRequest.productId());
            lineItem.setVendor(vendor);
            lineItem.setQuantity(itemRequest.quantity());
            lineItem.setUnitPrice(itemRequest.unitPrice());
            order.addLineItem(lineItem);
        }

        return orderRepository.save(order);
    }

    private void assertActorMayModifyStatus(Order order, User actor) {
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

    private void assertActorMayView(Order order, User viewer) {
        if (viewer.getRole() == Role.ADMIN) {
            return;
        }
        if (viewer.getRole() == Role.CUSTOMER && order.getCustomer().getId().equals(viewer.getId())) {
            return;
        }
        if (viewer.getRole() == Role.VENDOR) {
            boolean ownsAtLeastOneItem = order.getLineItems().stream()
                    .anyMatch(item -> item.belongsToVendor(viewer.getId()));
            if (ownsAtLeastOneItem) {
                return;
            }
        }
        throw new SecurityException(
                "User " + viewer.getId() + " is not permitted to view order " + order.getId());
    }
}
