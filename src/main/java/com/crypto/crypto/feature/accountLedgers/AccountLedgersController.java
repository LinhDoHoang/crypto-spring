package com.crypto.crypto.feature.accountLedgers;

import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.feature.accountLedgers.dto.AccountLedgersResponse;
import com.crypto.crypto.feature.accountLedgers.dto.CreateAccountLedgerDto;
import com.crypto.crypto.feature.accountLedgers.dto.UpdateAccountLedgerDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/account-ledgers")
public class AccountLedgersController {
    private final AccountLedgersService accountLedgersService;

    AccountLedgersController(AccountLedgersService accountLedgersService) {
        this.accountLedgersService = accountLedgersService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountLedgersResponse>> create(@RequestBody @Valid CreateAccountLedgerDto createAccountLedgerDto) {
        AccountLedgersResponse newAccountLedger = this.accountLedgersService.create(createAccountLedgerDto);
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Create account ledger successfully", newAccountLedger));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountLedgersResponse>>> getAll() {
        List<AccountLedgersResponse> accountLedgersResponses = this.accountLedgersService.getAll();
        return ResponseEntity.ok(ApiResponse.success("Get all account ledgers successfully", accountLedgersResponses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountLedgersResponse>> getOne(@PathVariable("id") @Positive Long id) {
        AccountLedgersResponse accountLedger = accountLedgersService.getOne(id);
        return ResponseEntity.ok(ApiResponse.success("Find account ledger successfully", accountLedger));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountLedgersResponse>> update(@PathVariable("id") @Positive Long id,
                                                                      @RequestBody @Valid UpdateAccountLedgerDto updateAccountLedgerDto) {
        AccountLedgersResponse updatedAccountLedger = this.accountLedgersService.update(id, updateAccountLedgerDto);
        return ResponseEntity.ok(ApiResponse.success("Update account ledger successfully", updatedAccountLedger));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") @Positive Long id) {
        this.accountLedgersService.delete(id);
        return ResponseEntity.noContent().build();
    }


}
