package lk.sliit.electronest.admin.service;

import lk.sliit.electronest.admin.dto.RegisterForm;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.admin.exception.DuplicateResourceException;
import lk.sliit.electronest.common.repository.UserRepository;
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
        String email = form.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Seller access requires approval; public registration always creates a customer.

        User user = User.builder()
                .fullName(form.getFullName().trim())
                .email(email)
                .password(passwordEncoder.encode(form.getPassword()))
                .contactNumber(form.getContactNumber() == null ? null : form.getContactNumber().trim())
                .role(Role.CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .build();

        return userRepository.save(user);
    }
}
