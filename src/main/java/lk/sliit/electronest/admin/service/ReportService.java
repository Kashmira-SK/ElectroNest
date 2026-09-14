package lk.sliit.electronest.admin.service;

import lk.sliit.electronest.admin.dto.DashboardSummary;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.payment.model.Payment;
import lk.sliit.electronest.payment.model.PaymentStatus;
import lk.sliit.electronest.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public DashboardSummary getDashboardSummary() {

        List<User> users = userRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();

        long activeVendors = users.stream()
                .filter(user -> user.getRole() == Role.VENDOR)
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .count();

        long successfulPayments = countPayments(
                payments,
                PaymentStatus.SUCCESSFUL
        );

        long pendingPayments = countPayments(
                payments,
                PaymentStatus.PENDING
        );

        long failedPayments = countPayments(
                payments,
                PaymentStatus.FAILED
        );

        long cancelledPayments = countPayments(
                payments,
                PaymentStatus.CANCELLED
        );

        long refundedPayments = countPayments(
                payments,
                PaymentStatus.REFUNDED
        );

        BigDecimal revenue = payments.stream()
                .filter(payment ->
                        payment.getPaymentStatus() == PaymentStatus.SUCCESSFUL)
                .map(Payment::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DashboardSummary.builder()
                .totalUsers(userRepository.count())
                .totalCustomers(userRepository.countByRole(Role.CUSTOMER))
                .totalVendors(userRepository.countByRole(Role.VENDOR))
                .activeVendors(activeVendors)
                .totalAdmins(userRepository.countByRole(Role.ADMIN))
                .activeAccounts(
                        userRepository.countByStatus(AccountStatus.ACTIVE)
                )
                .deactivatedAccounts(
                        userRepository.countByStatus(AccountStatus.DEACTIVATED)
                )
                .suspendedAccounts(
                        userRepository.countByStatus(AccountStatus.SUSPENDED)
                )
                .totalOrders(orderRepository.count())
                .totalPayments(payments.size())
                .successfulPayments(successfulPayments)
                .pendingPayments(pendingPayments)
                .failedPayments(failedPayments)
                .cancelledPayments(cancelledPayments)
                .refundedPayments(refundedPayments)
                .totalRevenue(revenue.doubleValue())
                .build();
    }

    private long countPayments(List<Payment> payments,
                               PaymentStatus status) {
        return payments.stream()
                .filter(payment -> payment.getPaymentStatus() == status)
                .count();
    }
}
