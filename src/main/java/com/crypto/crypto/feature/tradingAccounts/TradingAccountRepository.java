package com.crypto.crypto.feature.tradingAccounts;

import com.crypto.crypto.entities.TradingAccountsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TradingAccountRepository extends JpaRepository<TradingAccountsEntity, Long> {
    @Modifying
    @Query(value = "UPDATE TradingAccountsEntity ta SET ta.deletedAt = CURRENT_TIMESTAMP WHERE ta.id = :id")
    void softDelete(@Param("id") Long id);
}
