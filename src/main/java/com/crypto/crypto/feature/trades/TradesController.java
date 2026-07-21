package com.crypto.crypto.feature.trades;

import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.feature.trades.dto.CreateTradeDto;
import com.crypto.crypto.feature.trades.dto.TradeResponse;
import com.crypto.crypto.feature.trades.dto.UpdateTradeDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/trades")
public class TradesController {
    private final TradesService tradesService;

    TradesController(TradesService tradesService) {
        this.tradesService = tradesService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TradeResponse>> create(@RequestBody @Valid CreateTradeDto dto) {
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Create trade successfully", tradesService.create(dto)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TradeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Get all trades successfully", tradesService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TradeResponse>> getOne(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Find trade successfully", tradesService.getOne(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<TradeResponse>> update(
            @PathVariable @Positive Long id,
            @RequestBody @Valid UpdateTradeDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Update trade successfully", tradesService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        tradesService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
