package lk.sliit.electronest.admin.repository;

import com.electronest.admin.entity.Payment;
import com.electronest.admin.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    long countByStatus(PaymentStatus status);

    // Revenue = sum of all SUCCESSFUL payments. Returns null if there are
    // no matching rows yet, so callers must fall back to BigDecimal.ZERO.
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(PaymentStatus status);
}
