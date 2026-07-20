package com.crypto.crypto.feature.accountLedgers.dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;
import org.springframework.validation.annotation.Validated;

@Validated
@Getter
@Setter
public class CreateAccountLedgerDto {
    @NotNull
    @Min(1)
    private Long accountId;

    @Min(1)
    private Long orderId;

    @Length(max = 30)
    private String type;

    private Float amount;

    private Float balanceBefore;

    private Float balanceAfter;

    private String description;
}
