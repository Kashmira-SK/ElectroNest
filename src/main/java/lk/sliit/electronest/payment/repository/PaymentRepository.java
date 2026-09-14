package lk.sliit.electronest.payment.repository;

import lk.sliit.electronest.payment.model.Payment;
import lk.sliit.electronest.payment.model.PaymentMethod;
import lk.sliit.electronest.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByCustomerId(Long customerId);

    List<Payment> findByPaymentStatus(PaymentStatus status);

    List<Payment> findByPaymentMethod(PaymentMethod method);

    boolean existsByTransactionId(String transactionId);
}