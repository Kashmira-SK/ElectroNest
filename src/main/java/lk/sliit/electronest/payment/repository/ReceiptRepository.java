package lk.sliit.electronest.payment.repository;

import lk.sliit.electronest.payment.model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByReceiptNumber(String receiptNumber);

    Optional<Receipt> findByPaymentId(Long paymentId);

    Optional<Receipt> findByPayment_TransactionId(String transactionId);
}