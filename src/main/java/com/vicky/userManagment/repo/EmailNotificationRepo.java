package com.vicky.userManagment.repo;

import com.vicky.userManagment.entity.EmailNotification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface EmailNotificationRepo extends JpaRepository<EmailNotification, Long> {
    @Query("select n.id from EmailNotification n where n.status = :status and n.nextAttemptAt <= :now order by n.nextAttemptAt, n.id")
    List<Long> findDueIds(@Param("status") EmailNotification.Status status,
                          @Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from EmailNotification n where n.id = :id")
    Optional<EmailNotification> findLockedById(@Param("id") Long id);
}