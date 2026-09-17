package lk.sliit.electronest.admin.service;

import lk.sliit.electronest.admin.dto.RegisterForm;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationTest {
    @Test
    void registrationNormalizesEmailAndCannotGrantSellerOrAdminRole() {
        var users = mock(UserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode("Secret123")).thenReturn("hashed");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        var form = new RegisterForm();
        form.setFullName(" Customer ");
        form.setEmail(" Customer@Example.COM ");
        form.setPassword("Secret123");
        form.setConfirmPassword("Secret123");
        form.setRole(Role.ADMIN);
        User result = new AuthService(users, encoder).register(form);
        assertEquals("customer@example.com", result.getEmail());
        assertEquals("Customer", result.getFullName());
        assertEquals(Role.CUSTOMER, result.getRole());
        verify(users).existsByEmailIgnoreCase("customer@example.com");
    }

    @Test
    void loginAcceptsCapitalizationForNormalizedAccounts() {
        var users = mock(UserRepository.class);
        User user = new User();
        user.setEmail("customer@example.com");
        when(users.findByEmail("Customer@Example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        assertEquals("customer@example.com",
                new CustomUserDetailsService(users).loadUserByUsername(" Customer@Example.com ").getUsername());
    }

    @Test
    void duplicateEmailComparisonIgnoresCase() {
        var users = mock(UserRepository.class);
        when(users.existsByEmailIgnoreCase("customer@example.com")).thenReturn(true);
        var form = new RegisterForm();
        form.setEmail("CUSTOMER@example.com");
        assertThrows(lk.sliit.electronest.admin.exception.DuplicateResourceException.class,
                () -> new AuthService(users, mock(PasswordEncoder.class)).register(form));
        verify(users, never()).save(any());
    }
}
