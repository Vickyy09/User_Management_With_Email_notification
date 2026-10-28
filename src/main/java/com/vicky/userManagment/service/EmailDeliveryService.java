package com.vicky.userManagment.service;

import com.vicky.userManagment.entity.EmailNotification;
import com.vicky.userManagment.repo.EmailNotificationRepo;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;

@Service
public class EmailDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);
    private final EmailNotificationRepo notifications;
    private final JavaMailSender sender;
    private final Clock clock;
    private final String from;
    private final int maxAttempts;
    private final long retrySeconds;

    public EmailDeliveryService(EmailNotificationRepo notifications, JavaMailSender sender, Clock clock,
            @Value("${app.mail.from}") String from,
            @Value("${app.mail.max-attempts:3}") int maxAttempts,
            @Value("${app.mail.retry-seconds:30}") long retrySeconds) {
        if (maxAttempts < 1 || maxAttempts > 10 || retrySeconds < 1 || retrySeconds > 86400) {
            throw new IllegalArgumentException("Invalid email retry configuration");
        }
        this.notifications = notifications;
        this.sender = sender;
        this.clock = clock;
        this.from = from;
        this.maxAttempts = maxAttempts;
        this.retrySeconds = retrySeconds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long id) {
        var optional = notifications.findLockedById(id);
        if (optional.isEmpty()) return;
        var notification = optional.get();
        if (notification.getStatus() != EmailNotification.Status.PENDING
                || notification.getNextAttemptAt().isAfter(clock.instant())) return;
        notification.setAttempts(notification.getAttempts() + 1);
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.getRecipient());
        message.setSubject(notification.getSubject());
        message.setText(notification.getBody());
        try {
            sender.send(message);
            notification.setStatus(EmailNotification.Status.SENT);
            notification.setSentAt(clock.instant());
            notification.setLastError(null);
        } catch (MailException ex) {
            // Store only the exception category; provider messages can contain credentials or PII.
            notification.setLastError(ex.getClass().getSimpleName());
            boolean permanent = ex instanceof MailAuthenticationException || ex instanceof MailParseException;
            if (permanent || notification.getAttempts() >= maxAttempts) {
                notification.setStatus(EmailNotification.Status.FAILED);
            } else {
                long delay = Math.min(86400, retrySeconds * (1L << (notification.getAttempts() - 1)));
                notification.setNextAttemptAt(clock.instant().plusSeconds(delay));
            }
            log.warn("Email notification {}: attempt {}, status {}, error {}", id,
                    notification.getAttempts(), notification.getStatus(), notification.getLastError());
        }
    }
}