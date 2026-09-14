package com.electronest.admin.service;

import com.electronest.admin.dto.RegisterForm;
import com.electronest.admin.entity.AccountStatus;
import com.electronest.admin.entity.Role;
import com.electronest.admin.entity.User;
import com.electronest.admin.exception.DuplicateResourceException;
import com.electronest.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the "User Authentication & Profiles" sub-function - registration.
 * Login itself is handled entirely by Spring Security's form-login
 * (see SecurityConfig) since we're a server-rendered Thymeleaf app, not a
 * REST API - Spring Security manages the session for us.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterForm form) {
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Public registration is only ever allowed to create CUSTOMER or VENDOR
        // accounts. ADMIN accounts must be promoted by an existing admin
        // (see UserService.updateRole).
        if (form.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("You cannot self-register as an Administrator");
        }

        User user = User.builder()
                .fullName(form.getFullName())
                .email(form.getEmail())
                .password(passwordEncoder.encode(form.getPassword()))
                .contactNumber(form.getContactNumber())
                .role(form.getRole())
                .status(AccountStatus.ACTIVE)
                .build();

        return userRepository.save(user);
    }
}
