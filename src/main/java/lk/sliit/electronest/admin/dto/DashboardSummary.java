package lk.sliit.electronest.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummary {

    private long totalUsers;
    private long totalCustomers;
    private long totalVendors;
    private long activeVendors;
    private long totalAdmins;

    private long activeAccounts;
    private long deactivatedAccounts;
    private long suspendedAccounts;

    private long totalOrders;

    private long totalPayments;
    private long successfulPayments;
    private long pendingPayments;
    private long failedPayments;
    private long cancelledPayments;
    private long refundedPayments;

    private double totalRevenue;
}
