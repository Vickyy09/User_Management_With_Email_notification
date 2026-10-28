package com.vicky.userManagment.security;

import com.vicky.userManagment.entity.Role;

public record UserPrincipal(Long id, String email, Role role) {
    public boolean isAdmin() { return role == Role.ADMIN; }
}