package com.vicky.userManagment.service;

import com.vicky.userManagment.entity.Users;

import java.util.List;

public interface UserService {

    Users createUser(Users user);

    List<Users> getAllUsers();

    Users getUserById(Long id);

    Users updateUser(Long id, Users user);

    void deleteUser(Long id);
}

