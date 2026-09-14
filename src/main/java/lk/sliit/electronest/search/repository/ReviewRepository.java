package lk.sliit.electronest.search.repository;

import lk.sliit.electronest.search.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}