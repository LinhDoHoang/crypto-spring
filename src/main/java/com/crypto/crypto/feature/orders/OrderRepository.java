package com.crypto.crypto.feature.orders;

import com.crypto.crypto.entities.OrdersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<OrdersEntity, Long> {
}
