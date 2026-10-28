package com.vicky.userManagment.service;

import com.vicky.userManagment.entity.*;
import com.vicky.userManagment.repo.EmailNotificationRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.util.LinkedHashSet;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class EmailService {
    private final EmailNotificationRepo notifications;
    private final Clock clock;
    private final String hrEmail;

    public EmailService(EmailNotificationRepo notifications, Clock clock,
                        @Value("${app.mail.hr-email:}") String hrEmail) {
        this.notifications = notifications;
        this.clock = clock;
        this.hrEmail = hrEmail;
    }

    public void queueUserCreatedEmail(Users user) {
        queue(user, "Account created",
                "Your account has been created.\nDepartment: " +
                        (user.getDepartment() == null ? "" : user.getDepartment()) +
                        "\nRole: " + user.getRole(), true);
    }

    public void queueUserUpdatedEmail(Users user) {
        queue(user, "Profile updated", "Your profile details have been updated.", true);
    }

    public void queueUserDeactivatedEmail(Users user) {
        queue(user, "Account deactivated", "Your account has been deactivated. Contact your administrator for assistance.", true);
    }

    public void queuePasswordChangedEmail(Users user) {
        queue(user, "Password changed",
                "Your password has been changed. If you did not make this change, contact your administrator immediately.", false);
    }

    private void queue(Users user, String subject, String text, boolean notifyHr) {
        var recipients = new LinkedHashSet<String>();
        recipients.add(user.getEmail());
        if (notifyHr && !hrEmail.isBlank()) recipients.add(hrEmail.trim().toLowerCase(java.util.Locale.ROOT));
        // Separate messages prevent partial recipient failure from retrying every recipient.
        for (String recipient : recipients) {
            var notification = new EmailNotification();
            notification.setRecipient(recipient);
            notification.setSubject(subject);
            notification.setBody("Hello " + user.getFirstName() + ",\n\n" + text + "\n\nUser Management");
            notification.setCreatedAt(clock.instant());
            notification.setNextAttemptAt(clock.instant());
            notifications.save(notification);
        }
    }
}