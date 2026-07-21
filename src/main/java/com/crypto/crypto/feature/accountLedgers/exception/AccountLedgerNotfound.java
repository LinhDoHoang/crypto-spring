package com.crypto.crypto.feature.accountLedgers.exception;

import com.crypto.crypto.config.ResourceNotFoundException;

public class AccountLedgerNotfound extends ResourceNotFoundException {
    public AccountLedgerNotfound(Long id) {
        super("Account ledger", id);
    }
}
