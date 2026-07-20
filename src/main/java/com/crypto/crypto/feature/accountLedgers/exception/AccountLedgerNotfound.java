package com.crypto.crypto.feature.accountLedgers.exception;

public class AccountLedgerNotfound extends RuntimeException {
    public AccountLedgerNotfound(Long id) {
        super("Account ledger id " + id + " not found!");
    }
}
