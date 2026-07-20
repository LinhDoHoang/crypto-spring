package com.crypto.crypto.feature.users;

import com.crypto.crypto.entities.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UsersRepository extends JpaRepository<UsersEntity, Long> {
    @Modifying
    @Query("UPDATE UsersEntity u SET u.deletedAt = CURRENT_TIMESTAMP where u.id = :id")
    void softDelete(@Param("id") Long id);
}
