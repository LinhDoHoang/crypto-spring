package com.crypto.crypto.feature.tradingAccounts;

import com.crypto.crypto.entities.TradingAccountsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradingAccountRepository extends JpaRepository<TradingAccountsEntity, Long> {
}
