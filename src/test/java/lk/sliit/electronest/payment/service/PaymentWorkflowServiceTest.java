package lk.sliit.electronest.payment.service;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.model.OrderLineItem;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.payment.model.Payment;
import lk.sliit.electronest.payment.model.PaymentMethod;
import lk.sliit.electronest.payment.model.PaymentRequest;
import lk.sliit.electronest.payment.model.PaymentStatus;
import lk.sliit.electronest.payment.repository.PaymentRepository;
import lk.sliit.electronest.payment.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWorkflowServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private PaymentWorkflowService paymentService;

    private User customer;
    private Order order;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(2L)
                .fullName("Demo Customer")
                .email("customer@electronest.lk")
                .role(Role.CUSTOMER)
                .build();

        order = new Order();
        order.setId(20L);
        order.setCustomer(customer);
        order.setDeliveryName("Demo Customer");
        order.setDeliveryPhone("0771234567");
        order.setAddressLine1("42 Test Street");
        order.setCity("Colombo");
        order.setCountry("Sri Lanka");

        OrderLineItem item = new OrderLineItem();
        item.setProductId(10L);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("12500.00"));
        order.addLineItem(item);
    }

    @Test
    void paymentUsesOrderTotalAndAuthenticatedCustomer() {
        PaymentRequest request = request(PaymentMethod.CASH_ON_DELIVERY);
        request.setAmount(new BigDecimal("1.00"));
        request.setCustomerId(999L);

        Product product = new Product();
        product.setName("Studio Headphones");

        when(orderRepository.findById(20L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(20L)).thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productService.getProductById(10L)).thenReturn(product);

        Payment result = paymentService.processPayment(request, customer);

        assertEquals(new BigDecimal("25000.00"), result.getAmount());
        assertEquals(2L, result.getCustomerId());
        assertEquals("customer@electronest.lk", result.getCustomerEmail());
        assertEquals(lk.sliit.electronest.order.model.PaymentStatus.PAID,
                order.getPaymentStatus());
        verify(receiptRepository).save(any());
    }

    @Test
    void duplicateSuccessfulPaymentIsRejected() {
        Payment completed = new Payment();
        completed.setPaymentStatus(PaymentStatus.SUCCESSFUL);

        when(orderRepository.findById(20L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(20L)).thenReturn(List.of(completed));

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.processPayment(
                        request(PaymentMethod.CASH_ON_DELIVERY),
                        customer
                )
        );

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void cardPaymentRequiresValidCardDetails() {
        PaymentRequest request = request(PaymentMethod.CREDIT_CARD);
        request.setCardHolderName("Demo Customer");
        request.setCardNumber("not-a-card");

        assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.processPayment(request, customer)
        );

        verify(orderRepository, never()).findById(any());
    }

    private PaymentRequest request(PaymentMethod method) {
        PaymentRequest request = new PaymentRequest();
        request.setOrderId(20L);
        request.setPaymentMethod(method);
        return request;
    }
}
