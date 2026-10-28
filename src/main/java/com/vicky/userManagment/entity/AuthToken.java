package com.vicky.userManagment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "auth_tokens", indexes = @Index(name = "idx_token_expiry", columnList = "expiresAt"))
public class AuthToken {
    @Id
    @Column(length = 64)
    private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;
    @Column(nullable = false)
    private Instant expiresAt;
}