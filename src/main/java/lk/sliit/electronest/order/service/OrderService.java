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

    private final lk.sliit.electronest.cart.promo.PromoCodeService promos;
    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final VendorRepository vendorRepository;
    private final lk.sliit.electronest.payment.service.PaymentWorkflowService payments;

    public OrderService(OrderRepository orderRepository,
                        ProductService productService,
                        VendorRepository vendorRepository,
                        lk.sliit.electronest.payment.service.PaymentWorkflowService payments,
                        lk.sliit.electronest.cart.promo.PromoCodeService promos) {
        this.promos = promos;
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.vendorRepository = vendorRepository;
        this.payments = payments;
    }

    public boolean readyForFulfilment(Order order) {
        return payments.readyForFulfilment(order);
    }

    public List<Order> getOrderHistoryForCustomer(User customer) {
        return orderRepository.findByCustomer_Id(customer.getId());
    }

    public List<Order> getOrderHistoryForCustomerId(Long customerId) {
        return orderRepository.findByCustomer_Id(customerId);
    }

    public List<Order> getOrderQueueForVendor(User vendor) {
        requireApprovedSeller(vendor);
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
        Order order = orderRepository.findForUpdate(orderId).orElseThrow(() -> new NoSuchElementException("Order not found"));
        assertActorMayModifyStatus(order, actor);

        OrderStatus currentStatus = order.getStatus();

        if (targetStatus == null || (!currentStatus.canTransitionTo(targetStatus)
                && !(currentStatus == OrderStatus.DELIVERED && actor.getRole() == Role.ADMIN
                && adminOverride && targetStatus == OrderStatus.CANCELLED))) {
            throw new IllegalStateException(
                    "Cannot move order from " + currentStatus + " to " + targetStatus
            );
        }
        if (targetStatus == OrderStatus.CANCELLED) payments.cancelOrderPayment(order);
        else {
            if (!payments.readyForFulfilment(order)) throw new IllegalStateException("Payment or confirmed COD is required before fulfilment.");
            if (targetStatus == OrderStatus.DELIVERED) payments.collectCodOnDelivery(order);
        }
        order.setStatus(targetStatus);

        order.setCancellationRequested(false);
        order.setUpdatedAt(LocalDateTime.now());

        return orderRepository.save(order);
    }

    @Transactional
    public Order requestCancellation(Long orderId, User customer) {
        Order order = orderRepository.findForUpdate(orderId).orElseThrow(() -> new NoSuchElementException("Order not found"));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new SecurityException("You do not own this order");
        }

        if (order.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "Order is already " + order.getStatus()
            );
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            payments.cancelOrderPayment(order);

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
        if (customer == null || customer.getRole() != Role.CUSTOMER
                || customer.getStatus() != lk.sliit.electronest.common.model.AccountStatus.ACTIVE) {
            throw new SecurityException("An active customer account is required to create orders");
        }

        validateDelivery(request);

        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException(
                    "An order must have at least one item"
            );
        }

        var productIds = new java.util.HashSet<Long>();
        for (OrderLineItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.productId() <= 0 || item.quantity() <= 0) {
                throw new IllegalArgumentException("Each order item needs a valid product and positive quantity");
            }
            if (!productIds.add(item.productId())) {
                throw new IllegalArgumentException("Include each product only once and set its quantity");
            }
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
            Product product = productService.validateStockForOrder(
                    itemRequest.productId(),
                    itemRequest.quantity()
            );

            Vendor vendor = vendorRepository.findById(product.getVendorId())
                    .orElseThrow(() ->
                            new NoSuchElementException(
                                    "Vendor not found for product " + product.getId()
                            ));

            if (vendor.getStatus() != lk.sliit.electronest.vendor.model.VendorStatus.APPROVED
                    || vendor.getUser().getRole() != Role.VENDOR
                    || vendor.getUser().getStatus() != lk.sliit.electronest.common.model.AccountStatus.ACTIVE) {
                throw new IllegalStateException("This product's seller is currently unavailable");
            }

            OrderLineItem lineItem = new OrderLineItem();

            lineItem.setProductId(product.getId());
            lineItem.setVendor(vendor.getUser());
            lineItem.setQuantity(itemRequest.quantity());
            lineItem.setUnitPrice(product.getPrice());

            order.addLineItem(lineItem);
        }

        // Snapshot the discount once, from trusted product prices. No request totals are accepted.
        if (request.promoCode() != null && !request.promoCode().isBlank()) {
            var quote = promos.quote(request.promoCode(), order.subtotalAmount(), order.getDeliveryFee());
            order.setPromoCode(quote.code());
            order.setDiscountAmount(quote.discount());
        }
        return orderRepository.save(order);
    }

    private void validateDelivery(CreateOrderRequest request) {
        if (request == null) throw new IllegalArgumentException("Order details are required");
        checkLength(request.deliveryName(), 100, "Delivery name");
        checkLength(request.deliveryPhone(), 20, "Delivery phone");
        checkLength(request.addressLine1(), 200, "Address line 1");
        checkLength(request.addressLine2(), 200, "Address line 2");
        checkLength(request.city(), 100, "City");
        checkLength(request.postalCode(), 20, "Postal code");
        checkLength(request.country(), 100, "Country");
        if (blank(request.deliveryName())) {
            throw new IllegalArgumentException("Delivery name is required");
        }

        if (blank(request.deliveryPhone())) {
            throw new IllegalArgumentException("Delivery phone is required");
        }

        if (!request.deliveryPhone().matches(lk.sliit.electronest.common.validation.DeliveryPhone.REGEX)) {
            throw new IllegalArgumentException("Enter a local or international phone number with 7 to 15 digits");
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
            requireApprovedSeller(actor);
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

    private void requireApprovedSeller(User user) {
        vendorRepository.findByUser_Id(user.getId())
                .filter(vendor -> vendor.getStatus() == lk.sliit.electronest.vendor.model.VendorStatus.APPROVED
                        && vendor.getUser().getRole() == Role.VENDOR
                        && vendor.getUser().getStatus() == lk.sliit.electronest.common.model.AccountStatus.ACTIVE)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "An active approved seller account is required"));
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
            requireApprovedSeller(viewer);
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

    private void checkLength(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(field + " must be " + maximum + " characters or fewer");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return blank(value) ? null : value.trim();
    }

}
