package lk.sliit.electronest.catalog.repository;

import lk.sliit.electronest.catalog.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Product p where p.id = :id")
    java.util.Optional<Product> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Query("select count(i) > 0 from OrderLineItem i where i.productId = :id")
    boolean hasOrderHistory(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select count(r) > 0 from Review r where r.productId = :id")
    boolean hasReviewHistory(@org.springframework.data.repository.query.Param("id") Long id);

    List<Product> findByCategory(String category);

    List<Product> findByNameContainingIgnoreCase(String keyword);

    List<Product> findByStockQuantityLessThan(int threshold);

    List<Product> findByVendorId(Long vendorId);

    List<Product> findByVendorIdAndStockQuantityLessThan(Long vendorId, int threshold);

    List<Product> findByBrandContainingIgnoreCase(String brand);
}
