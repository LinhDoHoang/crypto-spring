package com.crypto.crypto.feature.trades;

import com.crypto.crypto.entities.TradesEntity;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import com.crypto.crypto.feature.trades.dto.CandleResponse;
import com.crypto.crypto.feature.trades.dto.CandlesResponse;
import com.crypto.crypto.feature.trades.dto.LastPriceResponse;
import com.crypto.crypto.feature.trades.exception.MarketDataException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class MarketDataService {
    private static final long DEFAULT_CANDLE_COUNT = 200;
    private static final long MAX_CANDLE_COUNT = 2_000;

    private final TradesRepository tradesRepository;

    public MarketDataService(TradesRepository tradesRepository) {
        this.tradesRepository = tradesRepository;
    }

    @Transactional(readOnly = true)
    public List<LastPriceResponse> last(String symbols) {
        Collection<SymbolEnum> requestedSymbols = symbols == null || symbols.isBlank()
                ? List.of(SymbolEnum.values())
                : parseSymbols(symbols);

        List<LastPriceResponse> result = new ArrayList<>();
        for (SymbolEnum symbol : requestedSymbols) {
            tradesRepository.findTopByAssetOrderByTimeDesc(symbol)
                    .ifPresent(trade -> result.add(new LastPriceResponse(
                            trade.getAsset(),
                            trade.getTime(),
                            trade.getPrice(),
                            trade.getQuantity()
                    )));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public CandlesResponse candles(
            String asset,
            String timeframe,
            Long startTime,
            Long endTime
    ) {
        SymbolEnum symbol = normalizeSymbol(asset);
        long intervalSeconds = timeframeSeconds(timeframe);
        long endSeconds = endTime == null
                ? Instant.now().getEpochSecond()
                : epochSeconds(endTime);
        long startSeconds = startTime == null
                ? endSeconds - intervalSeconds * DEFAULT_CANDLE_COUNT
                : epochSeconds(startTime);

        if (startSeconds >= endSeconds) {
            throw invalid("startTime must be before endTime");
        }

        long bucketCount = (endSeconds - startSeconds + intervalSeconds - 1)
                / intervalSeconds;
        if (bucketCount > MAX_CANDLE_COUNT) {
            throw invalid("Candle range is too large");
        }

        List<TradesEntity> trades = tradesRepository
                .findByAssetAndTimeBetweenOrderByTimeAsc(
                        symbol,
                        Instant.ofEpochSecond(startSeconds),
                        Instant.ofEpochSecond(endSeconds)
                );

        Map<Long, CandleAccumulator> buckets = new TreeMap<>();
        for (TradesEntity trade : trades) {
            long bucket = Math.floorDiv(
                    trade.getTime().getEpochSecond(),
                    intervalSeconds
            ) * intervalSeconds;
            buckets.computeIfAbsent(bucket, ignored -> new CandleAccumulator())
                    .accept(trade);
        }

        List<CandleResponse> candles = buckets.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .toList();
        return new CandlesResponse(candles);
    }

    private Collection<SymbolEnum> parseSymbols(String symbols) {
        Map<SymbolEnum, Boolean> unique = new LinkedHashMap<>();
        for (String value : symbols.split(",")) {
            unique.put(normalizeSymbol(value), Boolean.TRUE);
        }
        return unique.keySet();
    }

    private SymbolEnum normalizeSymbol(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("Asset is required");
        }

        String value = raw.trim().toUpperCase();
        return switch (value) {
            case "BTC", "BTCUSD", "BTCUSDT" -> SymbolEnum.BTCUSDT;
            case "ETH", "ETHUSD", "ETHUSDT" -> SymbolEnum.ETHUSDT;
            case "SOL", "SOLUSD", "SOLUSDT" -> SymbolEnum.SOLUSDT;
            default -> throw invalid("Unsupported asset: " + raw);
        };
    }

    private long timeframeSeconds(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            return 60;
        }
        return switch (timeframe.trim().toLowerCase()) {
            case "1m" -> 60;
            case "5m" -> 300;
            case "15m" -> 900;
            case "1h" -> 3_600;
            case "4h" -> 14_400;
            case "1d" -> 86_400;
            case "1w" -> 604_800;
            default -> throw invalid("Unsupported timeframe: " + timeframe);
        };
    }

    private long epochSeconds(long value) {
        return value > 100_000_000_000L ? value / 1_000 : value;
    }

    private MarketDataException invalid(String message) {
        return new MarketDataException(HttpStatus.BAD_REQUEST, message);
    }

    private static final class CandleAccumulator {
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume = BigDecimal.ZERO;

        private void accept(TradesEntity trade) {
            BigDecimal price = trade.getPrice();
            if (open == null) {
                open = price;
                high = price;
                low = price;
            } else {
                high = high.max(price);
                low = low.min(price);
            }
            close = price;
            volume = volume.add(trade.getQuantity());
        }

        private CandleResponse toResponse(long timestamp) {
            int decimal = Math.max(0, close.stripTrailingZeros().scale());
            return new CandleResponse(timestamp, open, high, low, close, volume, decimal);
        }
    }
}
