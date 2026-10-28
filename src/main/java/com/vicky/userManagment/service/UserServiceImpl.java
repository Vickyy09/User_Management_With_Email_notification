package com.vicky.userManagment.service;

import com.vicky.userManagment.dto.*;
import com.vicky.userManagment.entity.*;
import com.vicky.userManagment.exception.ApiException;
import com.vicky.userManagment.repo.*;
import com.vicky.userManagment.security.UserPrincipal;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepo users;
    private final AuthTokenRepo tokens;
    private final EmailService emails;
    private final PasswordEncoder encoder;

    public UserServiceImpl(UserRepo users, AuthTokenRepo tokens, EmailService emails, PasswordEncoder encoder) {
        this.users = users;
        this.tokens = tokens;
        this.emails = emails;
        this.encoder = encoder;
    }

    @Override
    public UserResponse register(CreateUserRequest request) {
        return create(request, Role.USER);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse createUser(CreateUserRequest request, UserPrincipal actor) {
        return create(request, Role.USER);
    }

    private UserResponse create(CreateUserRequest request, Role role) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        var user = new Users();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setPhoneNumber(request.phoneNumber());
        user.setDepartment(request.department());
        user.setRole(role);
        user.setPasswordHash(encoder.encode(request.password()));
        users.saveAndFlush(user);
        emails.queueUserCreatedEmail(user);
        return UserResponse.from(user);
    }

    public void bootstrapAdmin(CreateUserRequest request) {
        var existing = users.findByEmailIgnoreCase(normalize(request.email()));
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN || !existing.get().isActive()) {
                throw new IllegalStateException("Bootstrap email belongs to a non-admin or inactive account");
            }
            return;
        }
        create(request, Role.ADMIN);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> getAllUsers(int page, int size, String sort, String direction) {
        if (page < 0 || size < 1 || size > 100
                || !Set.of("id", "firstName", "lastName", "email", "createdAt").contains(sort)
                || !Set.of("asc", "desc").contains(direction.toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid pagination or sort parameters");
        }
        Sort order = Sort.by(Sort.Direction.fromString(direction), sort);
        if (!sort.equals("id")) order = order.and(Sort.by("id"));
        return PageResponse.from(users.findAll(PageRequest.of(page, size, order)).map(UserResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id, UserPrincipal actor) {
        requireOwnerOrAdmin(id, actor);
        return UserResponse.from(users.findById(id).orElseThrow(this::notFound));
    }

    @Override
    public UserResponse updateUser(Long id, UpdateUserRequest request, UserPrincipal actor) {
        requireOwnerOrAdmin(id, actor);
        Users user = users.findLockedById(id).orElseThrow(this::notFound);
        if (!user.isActive()) throw new ApiException(HttpStatus.CONFLICT, "Account is inactive");
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        boolean emailChanged = !email.equals(user.getEmail());
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setPhoneNumber(request.phoneNumber());
        user.setDepartment(request.department());
        users.flush();
        if (emailChanged) tokens.deleteAllForUser(id);
        emails.queueUserUpdatedEmail(user);
        return UserResponse.from(user);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivateUser(Long id) {
        Users user = users.findLockedById(id).orElseThrow(this::notFound);
        if (!user.isActive()) return;
        user.setActive(false);
        tokens.deleteAllForUser(id);
        emails.queueUserDeactivatedEmail(user);
    }

    @Override
    public void changePassword(ChangePasswordRequest request, UserPrincipal actor) {
        Users user = users.findLockedById(actor.id()).orElseThrow(this::notFound);
        if (!user.isActive()) throw new AccessDeniedException("Account is inactive");
        if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must differ from the current password");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        tokens.deleteAllForUser(user.getId());
        emails.queuePasswordChangedEmail(user);
    }

    private void requireOwnerOrAdmin(Long id, UserPrincipal actor) {
        if (actor == null || (!actor.isAdmin() && !actor.id().equals(id))) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "User not found"); }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}