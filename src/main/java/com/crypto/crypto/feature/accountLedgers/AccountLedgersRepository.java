package com.crypto.crypto.feature.accountLedgers;

import com.crypto.crypto.entities.AccountLedgersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountLedgersRepository extends JpaRepository<AccountLedgersEntity, Long> {
}
