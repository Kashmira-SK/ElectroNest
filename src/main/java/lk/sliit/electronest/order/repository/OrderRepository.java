package lk.sliit.electronest.order.repository;

import lk.sliit.electronest.order.model.Order; // 1. Me import eka add karanna
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            java.time.LocalDateTime from, java.time.LocalDateTime until);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    java.util.Optional<Order> findForUpdate(@Param("id") Long id);


    List<Order> findByCustomer_Id(Long customerId);


    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN o.lineItems li
            WHERE li.vendor.id = :vendorId
            """)
    List<Order> findByVendorId(@Param("vendorId") Long vendorId);
}
