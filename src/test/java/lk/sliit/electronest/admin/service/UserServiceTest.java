package lk.sliit.electronest.admin.service;

import lk.sliit.electronest.admin.dto.UpdateRoleForm;
import lk.sliit.electronest.admin.repository.RoleChangeLogRepository;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleChangeLogRepository roleChangeLogRepository;

    private UserService userService;
    private User admin;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleChangeLogRepository);
        admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
    }

    @Test
    void administratorCannotRemoveOwnAdminAccess() {
        UpdateRoleForm form = new UpdateRoleForm();
        form.setNewRole(Role.CUSTOMER);

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateRole(1L, form, admin)
        );

        verify(userRepository, never()).save(admin);
    }
}
