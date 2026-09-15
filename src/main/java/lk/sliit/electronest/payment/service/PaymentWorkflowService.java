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
import lk.sliit.electronest.payment.model.Receipt;
import lk.sliit.electronest.payment.repository.PaymentRepository;
import lk.sliit.electronest.payment.repository.ReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentWorkflowService {

    private final PaymentRepository paymentRepository;
    private final ReceiptRepository receiptRepository;
    private final OrderRepository orderRepository;
    private final ProductService productService;

    public PaymentWorkflowService(PaymentRepository paymentRepository,
                                  ReceiptRepository receiptRepository,
                                  OrderRepository orderRepository,
                                  ProductService productService) {
        this.paymentRepository = paymentRepository;
        this.receiptRepository = receiptRepository;
        this.orderRepository = orderRepository;
        this.productService = productService;
    }

    @Transactional
    public Payment processPayment(PaymentRequest request, User customer) {
        if (request.getOrderId() == null) {
            throw new IllegalArgumentException("Order is required");
        }

        if (request.getPaymentMethod() == null) {
            throw new IllegalArgumentException("Payment method is required");
        }

        validateMethodDetails(request);

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new SecurityException("You do not own this order");
        }

        if (order.getStatus().name().equals("CANCELLED")) {
            throw new IllegalStateException("Cannot pay for a cancelled order");
        }

        boolean alreadyPaid = paymentRepository.findByOrderId(order.getId())
                .stream()
                .anyMatch(payment -> payment.getPaymentStatus() == PaymentStatus.SUCCESSFUL);

        if (alreadyPaid) {
            throw new IllegalStateException("Order has already been paid");
        }

        Payment payment = new Payment();

        payment.setTransactionId("TXN-" + UUID.randomUUID());
        payment.setTransactionReference("SIM-" + UUID.randomUUID());
        payment.setOrderId(order.getId());
        payment.setOrderNumber(orderNumber(order));
        payment.setCustomerId(customer.getId());
        payment.setCustomerEmail(customer.getEmail());
        payment.setAmount(order.totalAmount());
        payment.setCurrency("LKR");
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        payment.setCardHolderName(clean(request.getCardHolderName()));
        payment.setMaskedCardNumber(maskCard(request.getCardNumber()));
        payment.setNotes("Simulated ElectroNest payment");

        Payment saved = paymentRepository.save(payment);

        order.setPaymentStatus(lk.sliit.electronest.order.model.PaymentStatus.PAID);
        orderRepository.save(order);

        createReceipt(saved, order, customer);

        return saved;
    }

    public List<Payment> myPayments(User customer) {
        return paymentRepository.findByCustomerId(customer.getId());
    }

    public Optional<Payment> findSuccessfulPaymentForOrderForViewer(
            Long orderId,
            User viewer) {

        return paymentRepository.findByOrderId(orderId).stream()
                .filter(payment -> payment.getPaymentStatus() == PaymentStatus.SUCCESSFUL)
                .peek(payment -> assertCanView(payment, viewer))
                .findFirst();
    }

    public Payment getPaymentForViewer(Long id, User viewer) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));

        assertCanView(payment, viewer);
        return payment;
    }

    public Payment getByTransactionForViewer(String transactionId, User viewer) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));

        assertCanView(payment, viewer);
        return payment;
    }

    public List<Payment> getOrderPaymentsForViewer(Long orderId, User viewer) {
        List<Payment> payments = paymentRepository.findByOrderId(orderId);

        payments.forEach(payment -> assertCanView(payment, viewer));
        return payments;
    }

    public List<Payment> getCustomerPayments(Long customerId) {
        return paymentRepository.findByCustomerId(customerId);
    }

    public List<Payment> getAllPayments(PaymentStatus status) {
        return status == null
                ? paymentRepository.findAll()
                : paymentRepository.findByPaymentStatus(status);
    }

    @Transactional
    public Payment updateStatus(Long id, PaymentStatus status) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));

        payment.setPaymentStatus(status);

        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        switch (status) {
            case SUCCESSFUL ->
                    order.setPaymentStatus(lk.sliit.electronest.order.model.PaymentStatus.PAID);
            case FAILED, CANCELLED ->
                    order.setPaymentStatus(lk.sliit.electronest.order.model.PaymentStatus.FAILED);
            case REFUNDED ->
                    order.setPaymentStatus(lk.sliit.electronest.order.model.PaymentStatus.REFUNDED);
            case PENDING ->
                    order.setPaymentStatus(lk.sliit.electronest.order.model.PaymentStatus.PENDING_PAYMENT);
        }

        orderRepository.save(order);

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment cancel(Long id, User actor) {
        Payment payment = getPaymentForViewer(id, actor);

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESSFUL) {
            throw new IllegalStateException("Successful payments cannot be cancelled");
        }

        return updateStatus(id, PaymentStatus.CANCELLED);
    }

    @Transactional
    public Payment refund(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));

        if (payment.getPaymentStatus() != PaymentStatus.SUCCESSFUL) {
            throw new IllegalStateException("Only successful payments can be refunded");
        }

        return updateStatus(id, PaymentStatus.REFUNDED);
    }

    public Receipt getReceiptForViewer(Long id, User viewer) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Receipt not found"));

        assertCanView(receipt.getPayment(), viewer);
        return receipt;
    }

    public Receipt getReceiptByNumberForViewer(String number, User viewer) {
        Receipt receipt = receiptRepository.findByReceiptNumber(number)
                .orElseThrow(() -> new NoSuchElementException("Receipt not found"));

        assertCanView(receipt.getPayment(), viewer);
        return receipt;
    }

    public Receipt getReceiptByPaymentForViewer(Long paymentId, User viewer) {
        Receipt receipt = receiptRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Receipt not found"));

        assertCanView(receipt.getPayment(), viewer);
        return receipt;
    }

    public Receipt getReceiptByTransactionForViewer(String transactionId, User viewer) {
        Receipt receipt = receiptRepository.findByPayment_TransactionId(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Receipt not found"));

        assertCanView(receipt.getPayment(), viewer);
        return receipt;
    }

    public List<Receipt> myReceipts(User customer) {
        return receiptRepository.findAll().stream()
                .filter(receipt ->
                        receipt.getPayment().getCustomerId().equals(customer.getId()))
                .toList();
    }

    public List<Receipt> allReceipts() {
        return receiptRepository.findAll();
    }

    private Receipt createReceipt(Payment payment,
                                  Order order,
                                  User customer) {
        Receipt receipt = new Receipt();

        receipt.setReceiptNumber("REC-" + UUID.randomUUID());
        receipt.setPayment(payment);
        receipt.setOrderNumber(orderNumber(order));
        receipt.setCustomerName(customer.getFullName());
        receipt.setCustomerEmail(customer.getEmail());
        receipt.setDeliveryAddress(deliveryAddress(order));
        receipt.setItemizedSummary(itemizedSummary(order));
        receipt.setSubtotal(order.totalAmount());
        receipt.setTaxAmount(BigDecimal.ZERO);
        receipt.setShippingFee(BigDecimal.ZERO);
        receipt.setDiscountAmount(BigDecimal.ZERO);
        receipt.setTotalAmount(order.totalAmount());
        receipt.setPaymentMethod(payment.getPaymentMethod().name());

        return receiptRepository.save(receipt);
    }

    private String itemizedSummary(Order order) {
        StringBuilder summary = new StringBuilder();

        for (OrderLineItem item : order.getLineItems()) {
            String name;

            try {
                Product product = productService.getProductById(item.getProductId());
                name = product.getName();
            } catch (RuntimeException ex) {
                name = "Product #" + item.getProductId();
            }

            if (!summary.isEmpty()) {
                summary.append("\n");
            }

            summary.append(name)
                    .append(" x ")
                    .append(item.getQuantity())
                    .append(" @ Rs. ")
                    .append(item.getUnitPrice());
        }

        return summary.toString();
    }

    private String deliveryAddress(Order order) {
        StringBuilder address = new StringBuilder(order.getAddressLine1());

        if (order.getAddressLine2() != null && !order.getAddressLine2().isBlank()) {
            address.append(", ").append(order.getAddressLine2());
        }

        address.append(", ").append(order.getCity());

        if (order.getPostalCode() != null && !order.getPostalCode().isBlank()) {
            address.append(" ").append(order.getPostalCode());
        }

        address.append(", ").append(order.getCountry());

        return address.toString();
    }

    private String orderNumber(Order order) {
        return "ORD-%06d".formatted(order.getId());
    }

    private void assertCanView(Payment payment, User viewer) {
        if (viewer.getRole() == Role.ADMIN) {
            return;
        }

        if (viewer.getRole() == Role.CUSTOMER &&
                payment.getCustomerId().equals(viewer.getId())) {
            return;
        }

        throw new SecurityException("You cannot access this payment");
    }

    private String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return null;
        }

        String digits = cardNumber.replaceAll("[\\s-]", "");

        if (!digits.matches("\\d{12,19}")) {
            throw new IllegalArgumentException("Enter a valid card number");
        }

        return "**** **** **** " + digits.substring(digits.length() - 4);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateMethodDetails(PaymentRequest request) {
        boolean cardPayment = request.getPaymentMethod() == PaymentMethod.CREDIT_CARD
                || request.getPaymentMethod() == PaymentMethod.DEBIT_CARD;

        if (!cardPayment) {
            return;
        }

        if (request.getCardHolderName() == null
                || request.getCardHolderName().isBlank()) {
            throw new IllegalArgumentException("Card holder name is required");
        }

        maskCard(request.getCardNumber());
    }
}
