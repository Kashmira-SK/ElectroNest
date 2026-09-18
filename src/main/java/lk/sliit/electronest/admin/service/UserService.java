package lk.sliit.electronest.admin.service;

import lk.sliit.electronest.admin.dto.UpdateRoleForm;
import lk.sliit.electronest.admin.dto.UpdateStatusForm;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.admin.entity.RoleChangeLog;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import lk.sliit.electronest.admin.exception.ResourceNotFoundException;
import lk.sliit.electronest.admin.repository.RoleChangeLogRepository;
import lk.sliit.electronest.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Manages account roles and status, recording changes in the audit log. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleChangeLogRepository roleChangeLogRepository;
    private final VendorRepository vendorRepository;

    public Vendor getVendorForUser(Long userId) {
        return vendorRepository.findByUser_Id(userId).orElse(null);
    }

    public List<Role> permittedRoles(Vendor vendor) {
        if (vendor == null || vendor.getStatus() == VendorStatus.REJECTED) {
            return List.of(Role.CUSTOMER, Role.ADMIN);
        }
        if (vendor.getStatus() == VendorStatus.APPROVED || vendor.getStatus() == VendorStatus.SUSPENDED) {
            return List.of(Role.VENDOR);
        }
        return List.of(Role.CUSTOMER);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return findUserOrThrow(id);
    }

    public List<User> searchUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllUsers();
        }
        return userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(keyword, keyword);
    }

    public List<RoleChangeLog> getAuditLogForUser(Long userId) {
        return roleChangeLogRepository.findByTargetUserIdOrderByChangedAtDesc(userId);
    }

    public List<RoleChangeLog> getAllAuditLogs() {
        return roleChangeLogRepository.findAllByOrderByChangedAtDesc();
    }

    @Transactional
    public void updateRole(Long targetUserId, UpdateRoleForm form, User performingAdmin) {
        preventSelfChange(targetUserId, performingAdmin);
        User user = findUserOrThrow(targetUserId);
        if (form.getNewRole() == null || !permittedRoles(getVendorForUser(targetUserId)).contains(form.getNewRole())) {
            throw new IllegalArgumentException(
                    "This role conflicts with the seller application. Use Vendor verification or Vendor management to change seller access.");
        }
        if (user.getRole() == form.getNewRole()) {
            return;
        }
        String previousRole = user.getRole().name();

        user.setRole(form.getNewRole());
        userRepository.save(user);

        roleChangeLogRepository.save(RoleChangeLog.builder()
                .targetUserId(user.getId())
                .targetUserEmail(user.getEmail())
                .performedByUserId(performingAdmin.getId())
                .performedByEmail(performingAdmin.getEmail())
                .previousRole(previousRole)
                .newRole(form.getNewRole().name())
                .reason(form.getReason())
                .build());
    }

    @Transactional
    public void updateStatus(Long targetUserId, UpdateStatusForm form, User performingAdmin) {
        preventSelfChange(targetUserId, performingAdmin);
        User user = findUserOrThrow(targetUserId);
        if (form.getNewStatus() == null) {
            throw new IllegalArgumentException("Please select an account status");
        }
        if (user.getStatus() == form.getNewStatus()) {
            return;
        }
        String previousStatus = user.getStatus().name();

        user.setStatus(form.getNewStatus());
        userRepository.save(user);

        roleChangeLogRepository.save(RoleChangeLog.builder()
                .targetUserId(user.getId())
                .targetUserEmail(user.getEmail())
                .performedByUserId(performingAdmin.getId())
                .performedByEmail(performingAdmin.getEmail())
                .previousStatus(previousStatus)
                .newStatus(form.getNewStatus().name())
                .reason(form.getReason())
                .build());
    }

    /** Deactivation preserves purchase and review history. */
    @Transactional
    public void deactivateUser(Long targetUserId, User performingAdmin) {
        preventSelfChange(targetUserId, performingAdmin);
        User user = findUserOrThrow(targetUserId);
        String previousStatus = user.getStatus().name();

        user.setStatus(AccountStatus.DEACTIVATED);
        userRepository.save(user);

        roleChangeLogRepository.save(RoleChangeLog.builder()
                .targetUserId(user.getId())
                .targetUserEmail(user.getEmail())
                .performedByUserId(performingAdmin.getId())
                .performedByEmail(performingAdmin.getEmail())
                .previousStatus(previousStatus)
                .newStatus("DEACTIVATED")
                .reason("Account deactivated by admin")
                .build());
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private void preventSelfChange(Long targetUserId, User performingAdmin) {
        if (performingAdmin != null
                && performingAdmin.getId() != null
                && performingAdmin.getId().equals(targetUserId)) {
            throw new IllegalArgumentException(
                    "Use another administrator account to change your own role or status"
            );
        }
    }
}
