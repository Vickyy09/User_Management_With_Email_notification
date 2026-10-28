package com.vicky.userManagment.dto;

import com.vicky.userManagment.entity.*;
import java.time.LocalDateTime;

public record UserResponse(Long id, String firstName, String lastName, String email,
                           String phoneNumber, String department, Role role, boolean active,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static UserResponse from(Users user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhoneNumber(), user.getDepartment(), user.getRole(),
                user.isActive(), user.getCreatedAt(), user.getUpdatedAt());
    }
}