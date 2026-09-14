package lk.sliit.electronest.search.repository;

import lk.sliit.electronest.catalog.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}