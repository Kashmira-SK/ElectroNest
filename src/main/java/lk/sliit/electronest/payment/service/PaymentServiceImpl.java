package lk.sliit.electronest.payment.service;

import lk.sliit.electronest.payment.model.Payment;
import lk.sliit.electronest.payment.model.PaymentRequest;
import lk.sliit.electronest.payment.model.PaymentStatus;
import lk.sliit.electronest.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Payment processPayment(PaymentRequest request) {

        // Build a brand-new entity here so no client-supplied id can ever
        // reach the repository and accidentally overwrite an existing payment.
        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setOrderNumber(request.getOrderNumber());
        payment.setCustomerId(request.getCustomerId());
        payment.setCustomerEmail(request.getCustomerEmail());
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setCardHolderName(request.getCardHolderName());

        payment.setTransactionId(
                "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );

        payment.setCurrency(
                (request.getCurrency() == null || request.getCurrency().isBlank())
                        ? "LKR"
                        : request.getCurrency()
        );

        payment.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        // Mask the raw card number from the request before it ever gets saved
        String rawCardNumber = request.getCardNumber();
        if (rawCardNumber != null && !rawCardNumber.isBlank()) {
            String digitsOnly = rawCardNumber.replaceAll("\\s+", "");
            if (digitsOnly.length() >= 4) {
                payment.setMaskedCardNumber(
                        "**** **** **** " +
                                digitsOnly.substring(digitsOnly.length() - 4)
                );
            }
        }

        return paymentRepository.save(payment);
    }

    @Override
    public Payment getPaymentById(Long id) {

        return paymentRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Payment not found with ID: " + id)
                );
    }

    @Override
    public Payment getPaymentByTransactionId(String transactionId) {

        return paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Payment not found with transaction ID: " + transactionId
                        )
                );
    }

    @Override
    public List<Payment> getPaymentsByOrderId(Long orderId) {

        return paymentRepository.findByOrderId(orderId);
    }

    @Override
    public List<Payment> getPaymentsByCustomerId(Long customerId) {

        return paymentRepository.findByCustomerId(customerId);
    }

    @Override
    public List<Payment> getAllPayments(PaymentStatus status) {

        if (status != null) {
            return paymentRepository.findByPaymentStatus(status);
        }

        return paymentRepository.findAll();
    }

    @Override
    public Payment updatePaymentStatus(Long id, PaymentStatus status) {

        Payment payment = getPaymentById(id);

        payment.setPaymentStatus(status);
        payment.setUpdatedAt(LocalDateTime.now());

        return paymentRepository.save(payment);
    }

    @Override
    public void cancelPayment(Long id) {

        Payment payment = getPaymentById(id);

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new RuntimeException(
                    "Only pending payments can be cancelled."
            );
        }

        payment.setPaymentStatus(PaymentStatus.CANCELLED);
        payment.setUpdatedAt(LocalDateTime.now());

        paymentRepository.save(payment);
    }

    @Override
    public Payment processRefund(Long id) {

        Payment payment = getPaymentById(id);

        if (payment.getPaymentStatus() != PaymentStatus.SUCCESSFUL) {
            throw new RuntimeException(
                    "Only successful payments can be refunded."
            );
        }

        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(LocalDateTime.now());

        return paymentRepository.save(payment);
    }
}