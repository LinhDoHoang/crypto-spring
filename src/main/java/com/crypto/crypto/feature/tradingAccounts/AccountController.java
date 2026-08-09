package com.crypto.crypto.feature.tradingAccounts;

import com.crypto.crypto.annotation.currentuser.CurrentUser;
import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.entities.UsersEntity;
import com.crypto.crypto.feature.accountLedgers.AccountLedgersService;
import com.crypto.crypto.feature.accountLedgers.dto.AccountLedgersResponse;
import com.crypto.crypto.feature.tradingAccounts.dto.AccountSnapshotResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/account")
public class AccountController {
    private final AccountSnapshotService accountSnapshotService;
    private final AccountLedgersService accountLedgersService;

    public AccountController(
            AccountSnapshotService accountSnapshotService,
            AccountLedgersService accountLedgersService
    ) {
        this.accountSnapshotService = accountSnapshotService;
        this.accountLedgersService = accountLedgersService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AccountSnapshotResponse>> get(
            @CurrentUser UsersEntity user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Account snapshot found successfully",
                accountSnapshotService.get(user.getId())
        ));
    }

    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<List<AccountLedgersResponse>>> ledger(
            @CurrentUser UsersEntity user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Account ledger found successfully",
                accountLedgersService.getForUser(user.getId())
        ));
    }
}
