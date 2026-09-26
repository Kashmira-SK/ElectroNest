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

    @Mock
    private SavedCardService savedCardService;
    @Mock
    private lk.sliit.electronest.cart.repository.CartItemRepository cartItemRepository;

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

        when(orderRepository.findForUpdate(20L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(20L)).thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productService.getProductById(10L)).thenReturn(product);

        Payment result = paymentService.processPayment(request, customer);

        assertEquals(new BigDecimal("25000.00"), result.getAmount());
        assertEquals(2L, result.getCustomerId());
        assertEquals("customer@electronest.lk", result.getCustomerEmail());
        assertEquals(lk.sliit.electronest.order.model.PaymentStatus.PENDING_PAYMENT,
                order.getPaymentStatus());
        assertEquals(PaymentStatus.PENDING, result.getPaymentStatus());
        verify(receiptRepository).save(any());
    }

    @Test
    void duplicateSuccessfulPaymentIsRejected() {
        Payment completed = new Payment();
        completed.setPaymentStatus(PaymentStatus.SUCCESSFUL);

        when(orderRepository.findForUpdate(20L)).thenReturn(Optional.of(order));
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

        verify(orderRepository, never()).findForUpdate(any());
    }

    @Test
    void savedCardPaymentUsesOwnedMetadataWithoutFullNumber() {
        PaymentRequest request = request(PaymentMethod.CREDIT_CARD);
        request.setSavedCardId(7L);
        request.setCvv("123");
        var card = new lk.sliit.electronest.payment.model.SavedCard();
        card.setMethod(PaymentMethod.DEBIT_CARD);
        card.setHolder("Saved Customer");
        card.setLast4("4242");
        card.setExpiry("12/39");
        when(savedCardService.owned(7L, customer)).thenReturn(card);
        when(orderRepository.findForUpdate(20L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(20L)).thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));
        Payment result = paymentService.processPayment(request, customer);
        assertEquals(PaymentMethod.DEBIT_CARD, result.getPaymentMethod());
        assertEquals("**** **** **** 4242", result.getMaskedCardNumber());
        assertEquals("Saved Customer", result.getCardHolderName());
    }

    @Test
    void unavailableSavedCardCannotChargeOrder() {
        PaymentRequest request = request(PaymentMethod.CREDIT_CARD);
        request.setSavedCardId(7L);
        when(savedCardService.owned(7L, customer)).thenThrow(new IllegalArgumentException("Unavailable"));
        assertThrows(IllegalArgumentException.class, () -> paymentService.processPayment(request, customer));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void newCardSaveUsesTheSameAccountService() {
        PaymentRequest request = request(PaymentMethod.CREDIT_CARD);
        request.setCardHolderName("Customer");
        request.setCardNumber("4242424242424242");
        request.setExpiryDate("12/39");
        request.setCvv("123");
        request.setSaveCard(true);
        when(orderRepository.findForUpdate(20L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(20L)).thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));
        paymentService.processPayment(request, customer);
        verify(savedCardService).save(customer, PaymentMethod.CREDIT_CARD, "Customer", "4242424242424242", "12/39");
    }

    @Test
    void pendingCodCannotBePurchasedTwice() {
        Payment cod = new Payment();
        cod.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        cod.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findForUpdate(20L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(20L)).thenReturn(List.of(cod));
        assertThrows(IllegalStateException.class,
                () -> paymentService.processPayment(request(PaymentMethod.CASH_ON_DELIVERY), customer));
        verify(productService, never()).decreaseStockForOrder(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void cancellationRefundsAndRestoresStockOnlyOnce() {
        Payment paid = new Payment();
        paid.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        when(paymentRepository.findByOrderId(20L)).thenReturn(List.of(paid));
        paymentService.cancelOrderPayment(order);
        paymentService.cancelOrderPayment(order);
        verify(productService).restoreStockForOrder(10L, 2);
        assertEquals(PaymentStatus.REFUNDED, paid.getPaymentStatus());
        assertEquals(lk.sliit.electronest.order.model.PaymentStatus.REFUNDED, order.getPaymentStatus());
    }

    @Test
    void codDeliveryCollectsCashWithoutDecreasingStockAgain() {
        Payment cod = new Payment();
        cod.setPaymentStatus(PaymentStatus.PENDING);
        cod.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        when(paymentRepository.findByOrderId(20L)).thenReturn(List.of(cod));
        paymentService.collectCodOnDelivery(order);
        assertEquals(PaymentStatus.SUCCESSFUL, cod.getPaymentStatus());
        assertEquals(lk.sliit.electronest.order.model.PaymentStatus.PAID, order.getPaymentStatus());
        verify(productService, never()).decreaseStockForOrder(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void deliveredRefundDoesNotAssumePhysicalGoodsWereReturned() {
        Payment paid = new Payment();
        paid.setOrderId(20L);
        paid.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        order.setStatus(lk.sliit.electronest.order.model.OrderStatus.DELIVERED);
        when(paymentRepository.findOrderId(1L)).thenReturn(Optional.of(20L));
        when(orderRepository.findForUpdate(20L)).thenReturn(Optional.of(order));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(paid));
        paymentService.refund(1L);
        assertEquals(PaymentStatus.REFUNDED, paid.getPaymentStatus());
        verify(productService, never()).restoreStockForOrder(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void unsupportedPaymentMethodCannotConsumeStock() {
        assertThrows(IllegalArgumentException.class,
                () -> paymentService.processPayment(request(PaymentMethod.DIGITAL_WALLET), customer));
        verify(productService, never()).decreaseStockForOrder(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void insufficientStockDoesNotCreatePaymentReceiptOrClearCart() {
        when(orderRepository.findForUpdate(20L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(20L)).thenReturn(List.of());
        when(productService.decreaseStockForOrder(10L, 2)).thenThrow(new IllegalStateException("Insufficient stock"));
        assertThrows(IllegalStateException.class,
                () -> paymentService.processPayment(request(PaymentMethod.CASH_ON_DELIVERY), customer));
        verify(paymentRepository, never()).save(any());
        verify(receiptRepository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(cartItemRepository);
    }

    @Test
    void cardNumberCannotHideInvalidCharactersAroundValidDigits() {
        PaymentRequest request = request(PaymentMethod.CREDIT_CARD);
        request.setCardHolderName("Customer");
        request.setCardNumber("bad4242424242424242");
        request.setExpiryDate("12/39");
        request.setCvv("123");
        assertThrows(IllegalArgumentException.class, () -> paymentService.processPayment(request, customer));
        verify(orderRepository, never()).findForUpdate(any());
    }

    @Test
    void overlongCardHolderIsRejectedBeforeStockIsChanged() {
        PaymentRequest request = request(PaymentMethod.CREDIT_CARD);
        request.setCardHolderName("X".repeat(101));
        assertThrows(IllegalArgumentException.class, () -> paymentService.processPayment(request, customer));
        verify(orderRepository, never()).findForUpdate(any());
    }

    private PaymentRequest request(PaymentMethod method) {
        PaymentRequest request = new PaymentRequest();
        request.setOrderId(20L);
        request.setPaymentMethod(method);
        return request;
    }
}
