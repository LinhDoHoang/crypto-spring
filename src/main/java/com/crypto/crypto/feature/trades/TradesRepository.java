package com.crypto.crypto.feature.trades;

import com.crypto.crypto.entities.TradesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradesRepository extends JpaRepository<TradesEntity, Long> {
}
