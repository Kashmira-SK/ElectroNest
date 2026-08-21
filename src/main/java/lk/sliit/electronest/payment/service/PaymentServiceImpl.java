package lk.sliit.electronest.payment.service;

import lk.sliit.electronest.payment.model.Payment;
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
    public Payment processPayment(Payment payment) {

        // Generate transaction ID
        payment.setTransactionId(
                "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );

        // Set default values
        if (payment.getCurrency() == null || payment.getCurrency().isBlank()) {
            payment.setCurrency("LKR");
        }

        payment.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        // Mask card number before saving
        if (payment.getMaskedCardNumber() != null
                && !payment.getMaskedCardNumber().isBlank()) {

            String cardNumber = payment.getMaskedCardNumber()
                    .replaceAll("\\s+", "");

            if (cardNumber.length() >= 4) {
                payment.setMaskedCardNumber(
                        "**** **** **** " +
                                cardNumber.substring(cardNumber.length() - 4)
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