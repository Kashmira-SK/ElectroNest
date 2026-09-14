package com.electronest.admin.service;

import com.electronest.admin.dto.DashboardSummary;
import com.electronest.admin.entity.AccountStatus;
import com.electronest.admin.entity.PaymentStatus;
import com.electronest.admin.entity.Role;
import com.electronest.admin.repository.OrderRepository;
import com.electronest.admin.repository.PaymentRepository;
import com.electronest.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Handles the "Platform Analytics" sub-function - the numbers shown on the
 * Admin Dashboard (Slide 6: "revenue, active vendor, order count, and
 * customer growth reports").
 *
 * User-related counts come from this module's own `users` table.
 * Order count and revenue are now real queries against the orders/payments
 * tables (see OrderRepository / PaymentRepository) instead of hardcoded
 * zeros - revenue is the sum of SUCCESSFUL payments only.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public DashboardSummary getDashboardSummary() {
        BigDecimal revenue = paymentRepository.sumAmountByStatus(PaymentStatus.SUCCESSFUL);

        return DashboardSummary.builder()
                .totalUsers(userRepository.count())
                .totalCustomers(userRepository.countByRole(Role.CUSTOMER))
                .totalVendors(userRepository.countByRole(Role.VENDOR))
                .totalAdmins(userRepository.countByRole(Role.ADMIN))
                .activeAccounts(userRepository.countByStatus(AccountStatus.ACTIVE))
                .deactivatedAccounts(userRepository.countByStatus(AccountStatus.DEACTIVATED))
                .suspendedAccounts(userRepository.countByStatus(AccountStatus.SUSPENDED))
                .totalOrders(orderRepository.count())
                .totalRevenue(revenue != null ? revenue.doubleValue() : 0.0)
                .build();
    }
}
