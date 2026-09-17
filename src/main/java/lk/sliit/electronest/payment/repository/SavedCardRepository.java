package lk.sliit.electronest.payment.repository;

import lk.sliit.electronest.payment.model.SavedCard;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SavedCardRepository extends JpaRepository<SavedCard, Long> {
    List<SavedCard> findByCustomerIdOrderByIdAsc(Long customerId);
    Optional<SavedCard> findByIdAndCustomerId(Long id, Long customerId);
}
