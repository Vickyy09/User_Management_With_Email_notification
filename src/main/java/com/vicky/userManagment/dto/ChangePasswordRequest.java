package com.vicky.userManagment.dto;

import com.vicky.userManagment.validation.ValidPassword;
import jakarta.validation.constraints.*;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @ValidPassword String newPassword) {
    @Override
    public String toString() { return "ChangePasswordRequest[redacted]"; }
}