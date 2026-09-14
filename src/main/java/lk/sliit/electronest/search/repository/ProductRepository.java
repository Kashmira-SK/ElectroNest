package lk.sliit.electronest.search.repository;

import lk.sliit.electronest.search.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}