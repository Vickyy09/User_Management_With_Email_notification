package com.vicky.userManagment.security;

import com.vicky.userManagment.dto.*;
import com.vicky.userManagment.entity.*;
import com.vicky.userManagment.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class TokenService {
    private final AuthTokenRepo tokens;
    private final UserRepo users;
    private final AuthenticationManager authenticationManager;
    private final Clock clock;
    private final long ttlMinutes;
    private final SecureRandom random = new SecureRandom();

    public TokenService(AuthTokenRepo tokens, UserRepo users, AuthenticationManager authenticationManager,
                        Clock clock, @Value("${app.auth.token-ttl-minutes:60}") long ttlMinutes) {
        if (ttlMinutes < 1 || ttlMinutes > 1440) throw new IllegalArgumentException("Token lifetime must be 1-1440 minutes");
        this.tokens = tokens;
        this.users = users;
        this.authenticationManager = authenticationManager;
        this.clock = clock;
        this.ttlMinutes = ttlMinutes;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        // Lock the account so password changes/deactivation cannot race token issuance.
        Users user = users.findLockedByEmail(email).orElse(null);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Invalid credentials");
        }
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        if (user == null || !user.isActive()) throw new BadCredentialsException("Invalid credentials");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = clock.instant().plus(Duration.ofMinutes(ttlMinutes));
        AuthToken token = new AuthToken();
        token.setTokenHash(hash(raw));
        token.setUser(user);
        token.setExpiresAt(expires);
        tokens.save(token);
        return new TokenResponse(raw, "Bearer", expires, UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public Optional<UserPrincipal> authenticate(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return tokens.findByTokenHash(hash(raw))
                .filter(token -> token.getExpiresAt().isAfter(clock.instant()))
                .filter(token -> token.getUser().isActive())
                .map(token -> new UserPrincipal(token.getUser().getId(),
                        token.getUser().getEmail(), token.getUser().getRole()));
    }

    @Transactional
    public void logout(String raw) { tokens.deleteById(hash(raw)); }

    @Transactional
    public void deleteExpired() { tokens.deleteExpired(clock.instant()); }

    public static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}