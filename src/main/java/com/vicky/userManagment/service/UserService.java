package com.vicky.userManagment.service;

import com.vicky.userManagment.dto.*;
import com.vicky.userManagment.security.UserPrincipal;

public interface UserService {
    UserResponse register(CreateUserRequest request);
    UserResponse createUser(CreateUserRequest request, UserPrincipal actor);
    PageResponse<UserResponse> getAllUsers(int page, int size, String sort, String direction);
    UserResponse getUserById(Long id, UserPrincipal actor);
    UserResponse updateUser(Long id, UpdateUserRequest request, UserPrincipal actor);
    void deactivateUser(Long id);
    void changePassword(ChangePasswordRequest request, UserPrincipal actor);
}