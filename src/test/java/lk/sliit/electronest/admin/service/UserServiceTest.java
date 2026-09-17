package lk.sliit.electronest.admin.service;

import lk.sliit.electronest.admin.dto.UpdateRoleForm;
import lk.sliit.electronest.admin.dto.UpdateStatusForm;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import java.util.Optional;
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
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleChangeLogRepository roleChangeLogRepository;

    @Mock
    private VendorRepository vendorRepository;

    private UserService userService;
    private User admin;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleChangeLogRepository, vendorRepository);
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

    @Test
    void vendorRoleRequiresApprovedProfile() {
        User target = target(Role.CUSTOMER);
        when(vendorRepository.findByUser_Id(2L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> changeRole(Role.VENDOR));
        verify(userRepository, never()).save(target);
        verify(roleChangeLogRepository, never()).save(any());
    }

    @Test
    void approvedAndSuspendedSellersCannotBeDemotedThroughRoles() {
        target(Role.VENDOR);
        Vendor vendor = new Vendor();
        when(vendorRepository.findByUser_Id(2L)).thenReturn(Optional.of(vendor));
        for (VendorStatus status : new VendorStatus[]{VendorStatus.APPROVED, VendorStatus.SUSPENDED}) {
            vendor.setStatus(status);
            assertThrows(IllegalArgumentException.class, () -> changeRole(Role.CUSTOMER));
            assertThrows(IllegalArgumentException.class, () -> changeRole(Role.ADMIN));
        }
        verify(roleChangeLogRepository, never()).save(any());
    }

    @Test
    void pendingApplicationCannotBypassApproval() {
        target(Role.CUSTOMER);
        Vendor vendor = new Vendor();
        when(vendorRepository.findByUser_Id(2L)).thenReturn(Optional.of(vendor));
        for (VendorStatus status : new VendorStatus[]{VendorStatus.PENDING, VendorStatus.INFO_REQUESTED}) {
            vendor.setStatus(status);
            assertThrows(IllegalArgumentException.class, () -> changeRole(Role.VENDOR));
            assertThrows(IllegalArgumentException.class, () -> changeRole(Role.ADMIN));
        }
    }

    @Test
    void ordinaryCustomerCanBecomeAdminWithAudit() {
        User target = target(Role.CUSTOMER);
        when(vendorRepository.findByUser_Id(2L)).thenReturn(Optional.empty());
        changeRole(Role.ADMIN);
        verify(userRepository).save(target);
        verify(roleChangeLogRepository).save(argThat(log ->
                "CUSTOMER".equals(log.getPreviousRole()) && "ADMIN".equals(log.getNewRole())
                        && admin.getId().equals(log.getPerformedByUserId())));
    }

    @Test
    void accountStatusChangesAreAuditedWithoutChangingSellerState() {
        User target = target(Role.VENDOR);
        UpdateStatusForm form = new UpdateStatusForm();
        form.setNewStatus(AccountStatus.SUSPENDED);
        userService.updateStatus(2L, form, admin);
        verify(userRepository).save(target);
        verify(roleChangeLogRepository).save(argThat(log ->
                "ACTIVE".equals(log.getPreviousStatus()) && "SUSPENDED".equals(log.getNewStatus())));
        verify(vendorRepository, never()).save(any());
    }

    @Test
    void administratorCannotSuspendSelf() {
        UpdateStatusForm form = new UpdateStatusForm();
        form.setNewStatus(AccountStatus.SUSPENDED);
        assertThrows(IllegalArgumentException.class, () -> userService.updateStatus(1L, form, admin));
    }

    private User target(Role role) {
        User target = new User();
        target.setId(2L);
        target.setRole(role);
        target.setStatus(AccountStatus.ACTIVE);
        target.setEmail("customer@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        return target;
    }

    private void changeRole(Role role) {
        UpdateRoleForm form = new UpdateRoleForm();
        form.setNewRole(role);
        userService.updateRole(2L, form, admin);
    }
}
