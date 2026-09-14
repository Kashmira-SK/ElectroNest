package lk.sliit.electronest.admin.repository;

import com.electronest.admin.entity.Order;
import com.electronest.admin.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    long countByStatus(OrderStatus status);
}
