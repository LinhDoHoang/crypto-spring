package com.crypto.crypto.feature.trades;

import com.crypto.crypto.entities.TradesEntity;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradesRepository extends JpaRepository<TradesEntity, Long> {
    Optional<TradesEntity> findTopByAssetOrderByTimeDesc(SymbolEnum asset);

    List<TradesEntity> findByAssetAndTimeBetweenOrderByTimeAsc(
            SymbolEnum asset,
            Instant start,
            Instant end
    );
}
