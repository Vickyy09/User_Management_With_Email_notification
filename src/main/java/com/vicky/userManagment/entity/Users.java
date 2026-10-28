package com.vicky.userManagment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80)
    private String firstName;
    @Column(length = 80)
    private String lastName;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(length = 30)
    private String phoneNumber;
    @Column(length = 100)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Role role = Role.USER;

    // Nullable only to accommodate accounts created before authentication existed.
    // Such accounts cannot log in until explicitly provisioned with a password.
    @JsonIgnore
    @Column(length = 100)
    private String passwordHash;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean active = true;

    @Version
    private Long version;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}