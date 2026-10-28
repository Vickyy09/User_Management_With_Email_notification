package com.vicky.userManagment.dto;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
    @Override
    public String toString() { return "TokenResponse[redacted]"; }
}