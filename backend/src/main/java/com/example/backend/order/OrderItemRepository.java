package com.example.backend.order;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByActiveTrueOrderByTypeAscNameAsc();
    Optional<OrderItem> findFirstByTypeAndNameAndActiveTrueOrderByIdAsc(OrderItem.Type type, String name);
}
