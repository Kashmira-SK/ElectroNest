package lk.sliit.electronest.search.repository;

import lk.sliit.electronest.search.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByCustomerIdAndProductId(
            Long customerId,
            Long productId
    );

    List<Review> findByProductIdAndStatusOrderByCreatedAtDesc(
            Long productId,
            String status
    );

    List<Review> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Review> findAllByOrderByCreatedAtDesc();
}
