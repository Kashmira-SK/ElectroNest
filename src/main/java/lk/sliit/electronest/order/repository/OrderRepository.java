package lk.sliit.electronest.order.repository;

import lk.sliit.electronest.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerId(Long customerId);

    /**
     * Vendor's order queue (UC-03 step 1) — every order containing at
     * least one line item belonging to this vendor.
     */
    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN o.lineItems li
            WHERE li.vendor.id = :vendorId
            """)
    List<Order> findByVendorId(@Param("vendorId") Long vendorId);
}
