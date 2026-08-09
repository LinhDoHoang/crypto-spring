package com.crypto.crypto.feature.trades;

import com.crypto.crypto.feature.trades.dto.CandlesResponse;
import com.crypto.crypto.feature.trades.dto.LastPriceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
public class MarketDataController {
    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/last")
    public ResponseEntity<List<LastPriceResponse>> last(
            @RequestParam(required = false) String symbols
    ) {
        return ResponseEntity.ok(marketDataService.last(symbols));
    }

    @GetMapping("/candles")
    public ResponseEntity<CandlesResponse> candles(
            @RequestParam(defaultValue = "BTCUSDT") String asset,
            @RequestParam(defaultValue = "1m") String ts,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime
    ) {
        return ResponseEntity.ok(marketDataService.candles(
                asset,
                ts,
                startTime,
                endTime
        ));
    }
}
