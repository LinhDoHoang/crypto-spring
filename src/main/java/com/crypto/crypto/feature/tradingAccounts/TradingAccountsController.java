package com.crypto.crypto.feature.tradingAccounts;

import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.feature.tradingAccounts.dto.CreateTradingAccountsDto;
import com.crypto.crypto.feature.tradingAccounts.dto.TradingAccountsResponse;
import com.crypto.crypto.feature.tradingAccounts.dto.UpdateTradingAccountsDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/trading-accounts")
public class TradingAccountsController {
    private final TradingAccountsService tradingAccountsService;

    TradingAccountsController(TradingAccountsService tradingAccountsService) {
        this.tradingAccountsService = tradingAccountsService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TradingAccountsResponse>> create(@RequestBody @Valid CreateTradingAccountsDto createTradingAccountsDto) {
        TradingAccountsResponse newTradingAccount = this.tradingAccountsService.create(createTradingAccountsDto);
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Create trading account successfully", newTradingAccount));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TradingAccountsResponse>>> getAll() {
        List<TradingAccountsResponse> existingTradingAccounts = this.tradingAccountsService.getAll();
        return ResponseEntity.ok(ApiResponse.success("Find all trading accounts successfully", existingTradingAccounts));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TradingAccountsResponse>> getOne(@PathVariable("id") @Positive Long id) {
        TradingAccountsResponse existingTradingAccount = this.tradingAccountsService.getOne(id);
        return ResponseEntity.ok(ApiResponse.success("Find trading account successfully", existingTradingAccount));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<TradingAccountsResponse>> update(@PathVariable("id") @Positive Long id,
                                                                       @RequestBody @Valid UpdateTradingAccountsDto updateTradingAccountsDto) {
        TradingAccountsResponse updatedTradingAccount = this.tradingAccountsService.update(id, updateTradingAccountsDto);
        return ResponseEntity.ok(ApiResponse.success("Update trading account " + id + " successfully", updatedTradingAccount));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") @Positive Long id) {
        this.tradingAccountsService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
