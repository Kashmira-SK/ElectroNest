package lk.sliit.electronest.payment.repository;

import lk.sliit.electronest.payment.model.Payment;
import lk.sliit.electronest.payment.model.PaymentMethod;
import lk.sliit.electronest.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            java.time.LocalDateTime from, java.time.LocalDateTime until);

    @org.springframework.data.jpa.repository.Query("select p.orderId from Payment p where p.id = :id")
    Optional<Long> findOrderId(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByCustomerId(Long customerId);

    List<Payment> findByPaymentStatus(PaymentStatus status);

    List<Payment> findByPaymentMethod(PaymentMethod method);

    boolean existsByTransactionId(String transactionId);
}
