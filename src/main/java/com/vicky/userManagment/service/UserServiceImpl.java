package com.vicky.userManagment.service;

import com.vicky.userManagment.entity.Users;
import com.vicky.userManagment.repo.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepo repo;

    @Autowired
    private EmailService emailService;

    @Override
    public Users createUser(Users user) {
        Users savedUser = repo.save(user);
        emailService.sendUserCreatedEmail(savedUser);
        return savedUser;
    }

    @Override
    public List<Users> getAllUsers() {

        return repo.findAll();
    }

    @Override
    public Users getUserById(Long id) {
        return repo.findById(id)
                .orElse(null);
    }

    @Override
    public Users updateUser(Long id, Users user) {
        Users existingUser = getUserById(id);

        existingUser.setFirstName(user.getFirstName());
        existingUser.setLastName(user.getLastName());
        existingUser.setEmail(user.getEmail());
        existingUser.setPhoneNumber(user.getPhoneNumber());
        existingUser.setDepartment(user.getDepartment());
        existingUser.setRole(user.getRole());
        Users updatedUser = repo.save(existingUser);
        emailService.sendUserUpdatedEmail(updatedUser);

        return updatedUser;
    }

    @Override
    public void deleteUser(Long id) {
        Users user = getUserById(id);
        repo.delete(user);
//        user.setActive(false);
//        repo.save(user);
        emailService.sendUserDeletedEmail(user);
    }
}

