package com.crypto.crypto.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@SQLRestriction("deleted_at IS NULL")
public class ModifiedEntity {
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "updated_by", insertable = false)
    private Long updatedBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;
}
