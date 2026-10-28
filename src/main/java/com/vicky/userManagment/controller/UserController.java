package com.vicky.userManagment.controller;

import com.vicky.userManagment.dto.*;
import com.vicky.userManagment.security.UserPrincipal;
import com.vicky.userManagment.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal UserPrincipal actor) {
        var user = service.createUser(request, actor);
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }

    @GetMapping
    public PageResponse<UserResponse> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return service.getAllUsers(page, size, sort, direction);
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal actor) {
        return service.getUserById(id, actor);
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable @Positive Long id, @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal actor) {
        return service.updateUser(id, request, actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateUser(@PathVariable @Positive Long id) { service.deactivateUser(id); }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal actor) {
        return service.getUserById(actor.id(), actor);
    }

    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal actor) {
        return service.updateUser(actor.id(), request, actor);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal actor) {
        service.changePassword(request, actor);
    }
}