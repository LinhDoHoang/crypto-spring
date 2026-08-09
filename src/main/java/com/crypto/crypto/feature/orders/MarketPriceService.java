package com.crypto.crypto.feature.orders;

import com.crypto.crypto.entities.TradesEntity;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import com.crypto.crypto.feature.orders.exception.OrderException;
import com.crypto.crypto.feature.trades.TradesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Service
public class MarketPriceService {
    private final TradesRepository tradesRepository;
    private final Duration maxPriceAge;

    public MarketPriceService(
            TradesRepository tradesRepository,
            @Value("${trading.max-price-age:PT5S}") Duration maxPriceAge
    ) {
        this.tradesRepository = tradesRepository;
        this.maxPriceAge = maxPriceAge;
    }

    public MarketPrice getFreshPrice(SymbolEnum symbol) {
        TradesEntity trade = tradesRepository.findTopByAssetOrderByTimeDesc(symbol)
                .orElseThrow(() -> new OrderException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Market price is unavailable"
                ));

        Instant now = Instant.now();
        Duration age = Duration.between(trade.getTime(), now);

        if (trade.getPrice() == null || trade.getPrice().signum() <= 0) {
            throw new OrderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Market price is invalid"
            );
        }

        if (age.isNegative() || age.compareTo(maxPriceAge) > 0) {
            throw new OrderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Market price is stale"
            );
        }

        return new MarketPrice(symbol, trade.getPrice(), trade.getTime());
    }

    public record MarketPrice(
            SymbolEnum symbol,
            BigDecimal price,
            Instant timestamp
    ) {
    }
}
