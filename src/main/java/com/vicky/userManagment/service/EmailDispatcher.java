package com.vicky.userManagment.service;

import com.vicky.userManagment.entity.EmailNotification;
import com.vicky.userManagment.repo.EmailNotificationRepo;
import org.slf4j.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;

@Service
public class EmailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);
    private final EmailNotificationRepo notifications;
    private final EmailDeliveryService delivery;
    private final Clock clock;

    public EmailDispatcher(EmailNotificationRepo notifications, EmailDeliveryService delivery, Clock clock) {
        this.notifications = notifications;
        this.delivery = delivery;
        this.clock = clock;
    }

    @Async("emailExecutor")
    public CompletableFuture<Void> dispatch() {
        var ids = notifications.findDueIds(EmailNotification.Status.PENDING, clock.instant(), PageRequest.of(0, 20));
        for (Long id : ids) {
            try {
                delivery.deliver(id);
            } catch (RuntimeException ex) {
                // A rolled-back attempt remains pending and will be picked up by a later poll.
                log.error("Email notification {} could not be processed: {}", id, ex.getClass().getSimpleName());
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}