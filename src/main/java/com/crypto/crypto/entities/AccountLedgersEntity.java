package com.crypto.crypto.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "account_ledgers")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountLedgersEntity extends ModifiedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "type")
    private String type;

    @Column(name = "amount")
    private Float amount;

    @Column(name = "balance_before")
    private Float balanceBefore;

    @Column(name = "balance_after")
    private Float balanceAfter;

    @Column(name = "description")
    private String description;
}
