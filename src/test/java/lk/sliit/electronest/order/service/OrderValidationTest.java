package lk.sliit.electronest.order.service;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.order.controller.dto.*;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.payment.service.PaymentWorkflowService;
import lk.sliit.electronest.vendor.model.*;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderValidationTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final ProductService products = mock(ProductService.class);
    final VendorRepository vendors = mock(VendorRepository.class);
    final OrderService service = new OrderService(orders, products, vendors, mock(PaymentWorkflowService.class));
    final User customer = User.builder().id(1L).role(Role.CUSTOMER).status(AccountStatus.ACTIVE).build();

    @Test void rejectsNullInvalidAndDuplicateItemsBeforeLookingUpProducts() {
        for (List<OrderLineItemRequest> items : List.of(
                Arrays.asList((OrderLineItemRequest) null),
                List.of(new OrderLineItemRequest(null, 1)),
                List.of(new OrderLineItemRequest(1L, 0)),
                List.of(new OrderLineItemRequest(1L, 3), new OrderLineItemRequest(1L, 3)))) {
            assertThrows(IllegalArgumentException.class, () -> service.createOrder(request("Customer", items), customer));
        }
        verifyNoInteractions(products, orders, vendors);
    }

    @Test void rejectsMissingOrOverlongDeliveryData() {
        assertThrows(IllegalArgumentException.class, () -> service.createOrder(null, customer));
        assertThrows(IllegalArgumentException.class, () -> service.createOrder(request("X".repeat(101), List.of()), customer));
        verifyNoInteractions(products, orders, vendors);
    }

    @Test void invalidDeliveryPhoneCannotBeSubmittedThroughTheApi() {
        var request = new CreateOrderRequest("Customer", "not a phone", "Street", null,
                "Colombo", null, "Sri Lanka", List.of(new OrderLineItemRequest(1L, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.createOrder(request, customer));
        verifyNoInteractions(products, orders, vendors);
    }

    @Test void suspendedCustomersCannotBypassControllerChecks() {
        customer.setStatus(AccountStatus.SUSPENDED);
        assertThrows(SecurityException.class, () -> service.createOrder(request("Customer", List.of()), customer));
        verifyNoInteractions(products, orders, vendors);
    }

    @Test void unavailableSellerCannotReceiveNewOrders() {
        Product product = new Product();
        product.setId(1L);
        product.setVendorId(2L);
        product.setPrice(BigDecimal.TEN);
        Vendor vendor = new Vendor();
        vendor.setStatus(VendorStatus.SUSPENDED);
        when(products.validateStockForOrder(1L, 1)).thenReturn(product);
        when(vendors.findById(2L)).thenReturn(Optional.of(vendor));
        assertThrows(IllegalStateException.class, () -> service.createOrder(
                request("Customer", List.of(new OrderLineItemRequest(1L, 1))), customer));
        verify(orders, never()).save(any());
    }

    CreateOrderRequest request(String name, List<OrderLineItemRequest> items) {
        return new CreateOrderRequest(name, "0771234567", "Street", null, "Colombo", null, "Sri Lanka", items);
    }
}
