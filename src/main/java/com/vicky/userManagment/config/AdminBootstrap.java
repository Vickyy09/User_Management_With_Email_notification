package com.vicky.userManagment.config;

import com.vicky.userManagment.dto.CreateUserRequest;
import com.vicky.userManagment.service.UserServiceImpl;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
    private final UserServiceImpl users;
    private final Validator validator;
    private final String email;
    private final String password;

    public AdminBootstrap(UserServiceImpl users, Validator validator,
            @Value("${app.bootstrap.email:}") String email,
            @Value("${app.bootstrap.password:}") String password) {
        this.users = users;
        this.validator = validator;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        var request = new CreateUserRequest("System", "Administrator", email, password, null, "Administration");
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalStateException("Bootstrap requires a valid email and a password of at least 12 characters / at most 72 UTF-8 bytes");
        }
        users.bootstrapAdmin(request);
    }
}