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
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
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
    private final SavedCardService savedCardService;
    private final lk.sliit.electronest.cart.repository.CartItemRepository cartItemRepository;

    public PaymentWorkflowService(PaymentRepository paymentRepository,
                                  ReceiptRepository receiptRepository,
                                  OrderRepository orderRepository,
                                  ProductService productService,
                                  SavedCardService savedCardService,
                                  lk.sliit.electronest.cart.repository.CartItemRepository cartItemRepository) {
        this.paymentRepository = paymentRepository;
        this.receiptRepository = receiptRepository;
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.savedCardService = savedCardService;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional
    public Payment processPayment(PaymentRequest request, User customer) {
        if (customer.getRole() != Role.CUSTOMER) throw new SecurityException("Only customers can pay");
        if (request.getOrderId() == null) {
            throw new IllegalArgumentException("Order is required");
        }

        var savedCard = request.getSavedCardId() == null ? null
                : savedCardService.owned(request.getSavedCardId(), customer);
        if (savedCard != null) {
            request.setPaymentMethod(savedCard.getMethod());
            savedCardService.validateExpiry(savedCard.getExpiry());
            validateCvv(request.getCvv());
        }

        if (request.getPaymentMethod() == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        if (request.getPaymentMethod() != PaymentMethod.CREDIT_CARD
                && request.getPaymentMethod() != PaymentMethod.DEBIT_CARD
                && request.getPaymentMethod() != PaymentMethod.CASH_ON_DELIVERY) {
            throw new IllegalArgumentException("Choose Credit Card, Debit Card or Cash on Delivery");
        }

        if (savedCard == null) validateMethodDetails(request);

        Order order = orderRepository.findForUpdate(request.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new SecurityException("You do not own this order");
        }

        if (order.getStatus().name().equals("CANCELLED")) {
            throw new IllegalStateException("Cannot pay for a cancelled order");
        }

        boolean alreadyPaid = paymentRepository.findByOrderId(order.getId())
                .stream()
                .anyMatch(payment -> payment.getPaymentStatus() == PaymentStatus.SUCCESSFUL
                        || payment.getPaymentStatus() == PaymentStatus.REFUNDED
                        || isPendingCod(payment));

        if (alreadyPaid) {
            throw new IllegalStateException("Order has already been paid");
        }
        if (order.getStatus() != lk.sliit.electronest.order.model.OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can begin payment");
        }

        for (OrderLineItem item : sortedItems(order)) {
            productService.decreaseStockForOrder(
                    item.getProductId(),
                    item.getQuantity()
            );
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
        boolean cod = request.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY;
        payment.setPaymentStatus(cod ? PaymentStatus.PENDING : PaymentStatus.SUCCESSFUL);
        boolean cardPayment = request.getPaymentMethod() == PaymentMethod.CREDIT_CARD
                || request.getPaymentMethod() == PaymentMethod.DEBIT_CARD;
        payment.setCardHolderName(savedCard != null ? savedCard.getHolder()
                : cardPayment ? clean(request.getCardHolderName()) : null);
        payment.setMaskedCardNumber(savedCard != null ? "**** **** **** " + savedCard.getLast4()
                : cardPayment ? maskCard(request.getCardNumber()) : null);
        payment.setNotes(cod ? "Cash due on delivery; stock reserved" : "Simulated ElectroNest payment");

        Payment saved = paymentRepository.save(payment);

        order.setPaymentStatus(cod ? lk.sliit.electronest.order.model.PaymentStatus.PENDING_PAYMENT
                : lk.sliit.electronest.order.model.PaymentStatus.PAID);
        orderRepository.save(order);

        createReceipt(saved, order, customer);

        if (savedCard == null && cardPayment && request.isSaveCard()) {
            savedCardService.save(customer, request.getPaymentMethod(), request.getCardHolderName(),
                    request.getCardNumber(), request.getExpiryDate());
        }
        for (OrderLineItem item : order.getLineItems()) {
            cartItemRepository.findByUserIdAndProductId(customer.getId(), item.getProductId()).ifPresent(cartItem -> {
                if (cartItem.getQuantity() <= item.getQuantity()) cartItemRepository.delete(cartItem);
                else {
                    cartItem.setQuantity(cartItem.getQuantity() - item.getQuantity());
                    cartItemRepository.save(cartItem);
                }
            });
        }

        return saved;
    }

    public List<Payment> myPayments(User customer) {
        return paymentRepository.findByCustomerId(customer.getId());
    }

    public Optional<Payment> findSuccessfulPaymentForOrderForViewer(
            Long orderId,
            User viewer) {

        return paymentRepository.findByOrderId(orderId).stream()
                .filter(payment -> payment.getPaymentStatus() == PaymentStatus.SUCCESSFUL || isPendingCod(payment))
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
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new NoSuchElementException("Order not found"));
        if (viewer.getRole() != Role.ADMIN && !order.getCustomer().getId().equals(viewer.getId())) {
            throw new SecurityException("You do not own this order");
        }
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
        Payment payment = lockedPayment(id);
        Order order = orderRepository.findForUpdate(payment.getOrderId()).orElseThrow();
        if (status == null) throw new IllegalArgumentException("Payment status is required");
        if (payment.getPaymentStatus() == status) return payment;
        if (status == PaymentStatus.REFUNDED && payment.getPaymentStatus() == PaymentStatus.SUCCESSFUL) {
            if (order.getStatus() != lk.sliit.electronest.order.model.OrderStatus.DELIVERED) {
                cancelOrderPayment(order);
                order.setStatus(lk.sliit.electronest.order.model.OrderStatus.CANCELLED);
            } else {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
                order.setPaymentStatus(lk.sliit.electronest.order.model.PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
            }
        } else if ((status == PaymentStatus.CANCELLED || status == PaymentStatus.FAILED)
                && payment.getPaymentStatus() == PaymentStatus.PENDING) {
            cancelOrderPayment(order);
            payment.setPaymentStatus(status);
            paymentRepository.save(payment);
            order.setStatus(lk.sliit.electronest.order.model.OrderStatus.CANCELLED);
        } else {
            throw new IllegalStateException("This payment transition is not allowed. Pay through checkout, collect COD on delivery, or refund a successful payment.");
        }
        order.setCancellationRequested(false);
        orderRepository.save(order);
        return payment;
    }

    private Payment lockedPayment(Long id) {
        Long orderId = paymentRepository.findOrderId(id)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));
        orderRepository.findForUpdate(orderId).orElseThrow(() -> new NoSuchElementException("Order not found"));
        return paymentRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Payment not found"));
    }

    @Transactional
    public Payment cancel(Long id, User actor) {
        Payment payment = lockedPayment(id);
        assertCanView(payment, actor);
        return updateStatus(id, PaymentStatus.CANCELLED);
    }

    @Transactional
    public Payment refund(Long id) {
        return updateStatus(id, PaymentStatus.REFUNDED);
    }

    /** Caller holds the order lock; cancellation releases stock only once. */
    public void cancelOrderPayment(Order order) {
        List<Payment> payments = paymentRepository.findByOrderId(order.getId());
        boolean allocated = payments.stream().anyMatch(p ->
                p.getPaymentStatus() == PaymentStatus.SUCCESSFUL || isPendingCod(p));
        if (allocated && order.getStatus() != lk.sliit.electronest.order.model.OrderStatus.CANCELLED
                && order.getStatus() != lk.sliit.electronest.order.model.OrderStatus.DELIVERED) {
            for (OrderLineItem item : sortedItems(order)) {
                productService.restoreStockForOrder(item.getProductId(), item.getQuantity());
            }
        }
        boolean refunded = payments.stream().anyMatch(p -> p.getPaymentStatus() == PaymentStatus.REFUNDED);
        for (Payment payment : payments) {
            if (payment.getPaymentStatus() == PaymentStatus.SUCCESSFUL) {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
                refunded = true;
                paymentRepository.save(payment);
            } else if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
                payment.setPaymentStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
            }
        }
        order.setPaymentStatus(refunded ? lk.sliit.electronest.order.model.PaymentStatus.REFUNDED
                : lk.sliit.electronest.order.model.PaymentStatus.FAILED);
    }

    public boolean readyForFulfilment(Order order) {
        return paymentRepository.findByOrderId(order.getId()).stream()
                .anyMatch(payment -> payment.getPaymentStatus() == PaymentStatus.SUCCESSFUL || isPendingCod(payment));
    }

    /** Marking COD delivered confirms that the seller collected the cash. */
    public void collectCodOnDelivery(Order order) {
        for (Payment payment : paymentRepository.findByOrderId(order.getId())) {
            if (isPendingCod(payment)) {
                payment.setPaymentStatus(PaymentStatus.SUCCESSFUL);
                paymentRepository.save(payment);
                order.setPaymentStatus(lk.sliit.electronest.order.model.PaymentStatus.PAID);
            }
        }
    }

    private boolean isPendingCod(Payment payment) {
        return payment.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY
                && payment.getPaymentStatus() == PaymentStatus.PENDING;
    }

    private List<OrderLineItem> sortedItems(Order order) {
        return order.getLineItems().stream()
                .sorted(java.util.Comparator.comparing(OrderLineItem::getProductId)).toList();
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

        String digits = cardNumber.replaceAll("\\D", "");

        if (!digits.matches("\\d{12,19}") || !isLuhnValid(digits)) {
            throw new IllegalArgumentException("Enter a valid card number");
        }

        return "**** **** **** " + digits.substring(digits.length() - 4);
    }

    private String normalizedCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new IllegalArgumentException("Card number is required");
        }

        String digits = cardNumber.replaceAll("[\\s-]", "");

        if (!digits.matches("\\d{12,19}") || !passesLuhn(digits)) {
            throw new IllegalArgumentException("Enter a valid card number");
        }

        return digits;
    }

    private boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleDigit = false;

        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';

            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            doubleDigit = !doubleDigit;
        }

        return sum % 10 == 0;
    }


    private boolean isLuhnValid(String digits) {
        int sum = 0;
        boolean doubleDigit = false;

        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';

            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            doubleDigit = !doubleDigit;
        }

        return sum % 10 == 0;
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

        String cardNumber = request.getCardNumber();

        if (cardNumber == null || cardNumber.isBlank()) {
            throw new IllegalArgumentException("Card number is required");
        }

        String digits = cardNumber.replaceAll("\\D", "");

        if (!digits.matches("\\d{12,19}") || !isLuhnValid(digits)) {
            throw new IllegalArgumentException("Enter a valid card number");
        }

        String expiry = request.getExpiryDate();

        if (expiry == null || !expiry.matches("\\d{2}/\\d{2}")) {
            throw new IllegalArgumentException("Enter expiry as MM/YY");
        }

        try {
            int month = Integer.parseInt(expiry.substring(0, 2));
            int year = 2000 + Integer.parseInt(expiry.substring(3, 5));

            YearMonth cardExpiry = YearMonth.of(year, month);

            if (cardExpiry.isBefore(YearMonth.now())) {
                throw new IllegalArgumentException("This card has expired");
            }
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw new IllegalArgumentException("Enter a valid card expiry date");
        }

        if (request.getCvv() == null
                || !request.getCvv().matches("\\d{3,4}")) {
            throw new IllegalArgumentException("CVV must be 3 or 4 digits");
        }
    }

    private void validateExpiryDate(String expiryDate) {
        if (expiryDate == null || expiryDate.isBlank()) {
            throw new IllegalArgumentException("Card expiry date is required");
        }

        try {
            YearMonth expiry = YearMonth.parse(expiryDate.trim());
            if (expiry.isBefore(YearMonth.now())) {
                throw new IllegalArgumentException("Card has expired");
            }
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Enter a valid card expiry date");
        }
    }

    private void validateCvv(String cvv) {
        if (cvv == null || !cvv.matches("\\d{3,4}")) {
            throw new IllegalArgumentException("CVV must contain 3 or 4 digits");
        }
    }
}
