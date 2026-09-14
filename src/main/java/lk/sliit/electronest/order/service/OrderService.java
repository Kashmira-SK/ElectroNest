package lk.sliit.electronest.order.service;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.order.controller.dto.CreateOrderRequest;
import lk.sliit.electronest.order.controller.dto.OrderLineItemRequest;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.model.OrderLineItem;
import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.order.model.PaymentStatus;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final VendorRepository vendorRepository;

    public OrderService(OrderRepository orderRepository,
                        ProductService productService,
                        VendorRepository vendorRepository) {
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.vendorRepository = vendorRepository;
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

    public List<Order> getOrderQueueForVendorId(Long vendorId) {
        return orderRepository.findByVendorId(vendorId);
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new NoSuchElementException("Order not found: " + orderId));
    }

    public Order getOrderByIdForViewer(Long orderId, User viewer) {
        Order order = getOrderById(orderId);
        assertActorMayView(order, viewer);
        return order;
    }

    @Transactional
    public Order updateFulfilmentStatus(Long orderId,
                                        User actor,
                                        OrderStatus targetStatus,
                                        boolean adminOverride) {
        Order order = getOrderById(orderId);

        assertActorMayModifyStatus(order, actor);

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == OrderStatus.DELIVERED &&
                actor.getRole() == Role.ADMIN &&
                adminOverride) {
            order.setStatus(targetStatus);
        } else if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    "Cannot move order from " + currentStatus + " to " + targetStatus
            );
        } else {
            order.setStatus(targetStatus);
        }

        order.setCancellationRequested(false);
        order.setUpdatedAt(LocalDateTime.now());

        return orderRepository.save(order);
    }

    @Transactional
    public Order requestCancellation(Long orderId, User customer) {
        Order order = getOrderById(orderId);

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new SecurityException("You do not own this order");
        }

        if (order.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "Order is already " + order.getStatus()
            );
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancellationRequested(false);
        } else {
            order.setCancellationRequested(true);
        }

        order.setUpdatedAt(LocalDateTime.now());

        return orderRepository.save(order);
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request, User customer) {
        if (customer.getRole() != Role.CUSTOMER) {
            throw new SecurityException("Only customers can create orders");
        }

        validateDelivery(request);

        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException(
                    "An order must have at least one item"
            );
        }

        Order order = new Order();

        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING_PAYMENT);

        order.setDeliveryName(request.deliveryName().trim());
        order.setDeliveryPhone(request.deliveryPhone().trim());
        order.setAddressLine1(request.addressLine1().trim());
        order.setAddressLine2(clean(request.addressLine2()));
        order.setCity(request.city().trim());
        order.setPostalCode(clean(request.postalCode()));
        order.setCountry(request.country().trim());

        order.setCancellationRequested(false);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        for (OrderLineItemRequest itemRequest : request.items()) {
            Product product = productService.decreaseStockForOrder(
                    itemRequest.productId(),
                    itemRequest.quantity()
            );

            Vendor vendor = vendorRepository.findById(product.getVendorId())
                    .orElseThrow(() ->
                            new NoSuchElementException(
                                    "Vendor not found for product " + product.getId()
                            ));

            OrderLineItem lineItem = new OrderLineItem();

            lineItem.setProductId(product.getId());
            lineItem.setVendor(vendor.getUser());
            lineItem.setQuantity(itemRequest.quantity());
            lineItem.setUnitPrice(product.getPrice());

            order.addLineItem(lineItem);
        }

        return orderRepository.save(order);
    }

    private void validateDelivery(CreateOrderRequest request) {
        if (blank(request.deliveryName())) {
            throw new IllegalArgumentException("Delivery name is required");
        }

        if (blank(request.deliveryPhone())) {
            throw new IllegalArgumentException("Delivery phone is required");
        }

        if (blank(request.addressLine1())) {
            throw new IllegalArgumentException("Delivery address is required");
        }

        if (blank(request.city())) {
            throw new IllegalArgumentException("City is required");
        }

        if (blank(request.country())) {
            throw new IllegalArgumentException("Country is required");
        }
    }

    private void assertActorMayModifyStatus(Order order, User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }

        if (actor.getRole() == Role.VENDOR) {
            boolean ownsItem = order.getLineItems().stream()
                    .anyMatch(item -> item.belongsToVendor(actor.getId()));

            if (ownsItem) {
                return;
            }
        }

        throw new SecurityException(
                "User is not permitted to modify this order"
        );
    }

    private void assertActorMayView(Order order, User viewer) {
        if (viewer.getRole() == Role.ADMIN) {
            return;
        }

        if (viewer.getRole() == Role.CUSTOMER &&
                order.getCustomer().getId().equals(viewer.getId())) {
            return;
        }

        if (viewer.getRole() == Role.VENDOR) {
            boolean ownsItem = order.getLineItems().stream()
                    .anyMatch(item -> item.belongsToVendor(viewer.getId()));

            if (ownsItem) {
                return;
            }
        }

        throw new SecurityException(
                "User is not permitted to view this order"
        );
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return blank(value) ? null : value.trim();
    }
}
