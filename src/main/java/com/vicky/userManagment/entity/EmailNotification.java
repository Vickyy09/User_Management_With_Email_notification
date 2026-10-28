package com.vicky.userManagment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "email_notifications", indexes =
        @Index(name = "idx_email_due", columnList = "status,nextAttemptAt"))
public class EmailNotification {
    public enum Status { PENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 254)
    private String recipient;
    @Column(nullable = false, length = 200)
    private String subject;
    @Column(nullable = false, length = 4000)
    private String body;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;
    private int attempts;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant nextAttemptAt;
    private Instant sentAt;
    @Column(length = 200)
    private String lastError;
}