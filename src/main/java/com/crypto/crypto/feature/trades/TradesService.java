package com.crypto.crypto.feature.trades;

import com.crypto.crypto.entities.TradesEntity;
import com.crypto.crypto.feature.trades.dto.CreateTradeDto;
import com.crypto.crypto.feature.trades.dto.TradeResponse;
import com.crypto.crypto.feature.trades.dto.UpdateTradeDto;
import com.crypto.crypto.feature.trades.exception.TradeNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TradesService {
    private final TradesRepository tradesRepository;

    TradesService(TradesRepository tradesRepository) {
        this.tradesRepository = tradesRepository;
    }

    @Transactional
    public TradeResponse create(CreateTradeDto dto) {
        TradesEntity trade = TradesEntity.builder()
                .time(dto.getTime())
                .asset(dto.getAsset())
                .price(dto.getPrice())
                .quantity(dto.getQuantity())
                .source(dto.getSource())
                .sourceTradeId(dto.getSourceTradeId())
                .build();
        return TradeResponse.from(tradesRepository.save(trade));
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> getAll() {
        return tradesRepository.findAll().stream()
                .map(TradeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TradeResponse getOne(Long id) {
        return TradeResponse.from(findById(id));
    }

    @Transactional
    public TradeResponse update(Long id, UpdateTradeDto dto) {
        TradesEntity trade = findById(id);

        if (dto.getTime() != null) trade.setTime(dto.getTime());
        if (dto.getAsset() != null) trade.setAsset(dto.getAsset());
        if (dto.getPrice() != null) trade.setPrice(dto.getPrice());
        if (dto.getQuantity() != null) trade.setQuantity(dto.getQuantity());
        if (dto.getSource() != null) trade.setSource(dto.getSource());
        if (dto.getSourceTradeId() != null) trade.setSourceTradeId(dto.getSourceTradeId());

        return TradeResponse.from(trade);
    }

    @Transactional
    public void delete(Long id) {
        TradesEntity trade = findById(id);
        trade.setDeletedAt(Instant.now());
    }

    private TradesEntity findById(Long id) {
        return tradesRepository.findById(id)
                .orElseThrow(() -> new TradeNotFoundException(id));
    }
}
