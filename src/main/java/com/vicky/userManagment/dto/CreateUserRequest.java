package com.vicky.userManagment.dto;

import com.vicky.userManagment.validation.ValidPassword;
import jakarta.validation.constraints.*;

public record CreateUserRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @ValidPassword String password,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$", message = "must be a valid phone number") String phoneNumber,
        @Size(max = 100) String department) {
    @Override
    public String toString() { return "CreateUserRequest[redacted]"; }
}