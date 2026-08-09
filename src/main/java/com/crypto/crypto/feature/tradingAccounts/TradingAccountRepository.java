package com.crypto.crypto.feature.tradingAccounts;

import com.crypto.crypto.entities.TradingAccountsEntity;
import com.crypto.crypto.feature.tradingAccounts.constant.AccountTypeEnum;
import com.crypto.crypto.feature.tradingAccounts.constant.StatusEnum;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradingAccountRepository extends JpaRepository<TradingAccountsEntity, Long> {
    Optional<TradingAccountsEntity> findByUserId(Long userId);

    Optional<TradingAccountsEntity> findByUserIdAndAccountTypeAndStatus(
            Long userId,
            AccountTypeEnum accountType,
            StatusEnum status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from TradingAccountsEntity account
            where account.userId = :userId
              and account.accountType = :accountType
              and account.status = :status
            """)
    Optional<TradingAccountsEntity> findForUpdate(
            @Param("userId") Long userId,
            @Param("accountType") AccountTypeEnum accountType,
            @Param("status") StatusEnum status
    );
}
