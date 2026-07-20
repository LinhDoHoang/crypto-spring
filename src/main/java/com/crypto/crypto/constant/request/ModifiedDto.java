package com.crypto.crypto.constant.request;

import lombok.Getter;

import java.time.Instant;

@Getter
public class ModifiedDto {
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
}
