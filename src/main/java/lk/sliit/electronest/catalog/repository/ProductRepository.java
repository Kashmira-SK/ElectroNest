package lk.sliit.electronest.catalog.repository;

import lk.sliit.electronest.catalog.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategory(String category);

    List<Product> findByNameContainingIgnoreCase(String keyword);

    List<Product> findByStockQuantityLessThan(int threshold);

    List<Product> findByVendorId(Long vendorId);

    List<Product> findByVendorIdAndStockQuantityLessThan(Long vendorId, int threshold);

    List<Product> findByBrandContainingIgnoreCase(String brand);
}