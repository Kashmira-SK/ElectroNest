package lk.sliit.electronest.search.repository;

import lk.sliit.electronest.catalog.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductSearchRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
}