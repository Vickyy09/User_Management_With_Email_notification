package com.vicky.userManagment.repo;

import com.vicky.userManagment.entity.AuthToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepo extends JpaRepository<AuthToken, String> {
    @EntityGraph(attributePaths = "user")
    Optional<AuthToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from AuthToken t where t.user.id = :userId")
    void deleteAllForUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}