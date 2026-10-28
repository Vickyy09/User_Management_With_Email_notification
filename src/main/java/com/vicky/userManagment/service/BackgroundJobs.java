package com.vicky.userManagment.service;

import com.vicky.userManagment.security.TokenService;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundJobs {
    private static final Logger log = LoggerFactory.getLogger(BackgroundJobs.class);
    private final EmailDispatcher dispatcher;
    private final TokenService tokens;

    public BackgroundJobs(EmailDispatcher dispatcher, TokenService tokens) {
        this.dispatcher = dispatcher;
        this.tokens = tokens;
    }

    @Scheduled(fixedDelayString = "${app.mail.poll-interval-ms:5000}")
    public void sendEmails() {
        try {
            dispatcher.dispatch().exceptionally(ex -> {
                log.error("Email poll failed: {}", ex.getClass().getSimpleName());
                return null;
            });
        } catch (TaskRejectedException ex) {
            // The worker is busy. Durable pending messages remain available for the next poll.
            log.debug("Email worker is busy");
        }
    }

    @Scheduled(fixedDelay = 3600000)
    public void purgeExpiredTokens() { tokens.deleteExpired(); }
}