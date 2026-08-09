package com.crypto.crypto.feature.trades.dto;

import java.util.List;

public record CandlesResponse(List<CandleResponse> candles) {
}
