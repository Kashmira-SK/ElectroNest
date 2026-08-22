package lk.sliit.electronest.admin.service;

import lk.sliit.electronest.admin.dto.DashboardSummary;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Handles the "Platform Analytics" sub-function - the numbers shown on the
 * Admin Dashboard (Slide 6: "revenue, active vendor, order count, and
 * customer growth reports").
 *
 * User-related counts are fully implemented here since the `users` table
 * belongs to this module. Order-count and revenue are wired as placeholders
 * (0) - once Konara's Order module and Peramuna's Payment module are merged
 * into the shared database, inject their repositories here and replace the
 * placeholders with real queries (see the TODOs below).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepository;

    public DashboardSummary getDashboardSummary() {
        return DashboardSummary.builder()
                .totalUsers(userRepository.count())
                .totalCustomers(userRepository.countByRole(Role.CUSTOMER))
                .totalVendors(userRepository.countByRole(Role.VENDOR))
                .totalAdmins(userRepository.countByRole(Role.ADMIN))
                .activeAccounts(userRepository.countByStatus(AccountStatus.ACTIVE))
                .deactivatedAccounts(userRepository.countByStatus(AccountStatus.DEACTIVATED))
                .suspendedAccounts(userRepository.countByStatus(AccountStatus.SUSPENDED))
                // TODO: replace with orderRepository.count() once Order module is merged
                .totalOrders(0)
                // TODO: replace with paymentRepository.sumSuccessfulPayments() once Payment module is merged
                .totalRevenue(0.0)
                .build();
    }
}
