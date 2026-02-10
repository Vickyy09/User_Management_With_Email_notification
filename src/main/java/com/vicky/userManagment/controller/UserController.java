package com.vicky.userManagment.controller;

import com.vicky.userManagment.entity.Users;
import com.vicky.userManagment.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService service;

    @PostMapping("/createUser")
    public String createUser(@RequestBody Users user) {
        service.createUser(user);
        return "User saved successfully";
    }

    @GetMapping("/getAll")
    public List<Users> getAllUsers() {
        return service.getAllUsers();
    }

    @GetMapping("/get/{id}")
    public Users getUserById(@PathVariable Long id) {
        return service.getUserById(id);
    }

    @PutMapping("/update/{id}")
    public Users updateUser(@PathVariable Long id, @RequestBody Users user) {
        return service.updateUser(id, user);
    }

    @DeleteMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id) {
        service.deleteUser(id);
        return "User deleted ";
    }
}
