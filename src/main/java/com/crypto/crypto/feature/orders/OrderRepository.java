package com.crypto.crypto.feature.orders;

import com.crypto.crypto.entities.OrdersEntity;
import com.crypto.crypto.feature.orders.constant.OrderStatusEnum;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrdersEntity, Long> {
    Optional<OrdersEntity> findByAccountIdAndClientOrderId(
            Long accountId,
            String clientOrderId
    );

    List<OrdersEntity> findByAccountIdAndStatus(
            Long accountId,
            OrderStatusEnum status
    );

    List<OrdersEntity> findByAccountIdOrderByOpenedAtDesc(Long accountId);

    Optional<OrdersEntity> findByIdAndAccountIdAndUserId(
            Long id,
            Long accountId,
            Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from OrdersEntity o
            where o.id = :orderId
              and o.accountId = :accountId
              and o.userId = :userId
            """)
    Optional<OrdersEntity> findForUpdate(
            @Param("orderId") Long orderId,
            @Param("accountId") Long accountId,
            @Param("userId") Long userId
    );
}
