package lk.sliit.electronest.payment.service;

import lk.sliit.electronest.payment.model.Payment;
import lk.sliit.electronest.payment.model.PaymentStatus;

import java.util.List;

public interface PaymentService {

    Payment processPayment(Payment payment);

    Payment getPaymentById(Long id);

    Payment getPaymentByTransactionId(String transactionId);

    List<Payment> getPaymentsByOrderId(Long orderId);

    List<Payment> getPaymentsByCustomerId(Long customerId);

    List<Payment> getAllPayments(PaymentStatus status);

    Payment updatePaymentStatus(Long id, PaymentStatus status);

    void cancelPayment(Long id);

    Payment processRefund(Long id);
}