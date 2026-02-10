package com.vicky.userManagment.service;
import com.vicky.userManagment.entity.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    private static final String HR_EMAIL = "hr@techcorp.com";

    public void sendUserCreatedEmail(Users user) {
        sendEmail(
                new String[]{HR_EMAIL, user.getEmail()},
                "Welcome to TechCorp - Account Created",
                buildCreateMessage(user)
        );
    }

    public void sendUserUpdatedEmail(Users user) {
        sendEmail(
                new String[]{HR_EMAIL, user.getEmail()},
                "Account Updated - User Details Changed",
                buildUpdateMessage(user)
        );
    }

    public void sendUserDeletedEmail(Users user) {
        sendEmail(
                new String[]{HR_EMAIL, user.getEmail()},
                "Account Deactivated - " + user.getFirstName(),
                buildDeleteMessage(user)
        );
    }

    private void sendEmail(String[] to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private String buildCreateMessage(Users user) {
        return "Hello " + user.getFirstName() + ",\n\n" +
                "Your account has been successfully created.\n\n" +
                "Department: " + user.getDepartment() + "\n" +
                "Role: " + user.getRole() + "\n\n" +
                "Regards,\nTechCorp HR";
    }

    private String buildUpdateMessage(Users user) {
        return "Hello " + user.getFirstName() + ",\n\n" +
                "Your account details were updated on " + user.getUpdatedAt() + ".\n\n" +
                "Regards,\nTechCorp HR";
    }

    private String buildDeleteMessage(Users user) {
        return "Hello " + user.getFirstName() + ",\n\n" +
                "Your account has been deactivated.\n" +
                "For any questions, contact HR.\n\n" +
                "Regards,\nTechCorp HR";
    }
}
