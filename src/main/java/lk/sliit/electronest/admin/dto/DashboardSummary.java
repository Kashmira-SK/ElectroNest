package lk.sliit.electronest.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary numbers shown on the Admin Dashboard.
 * NOTE: totalOrders / totalRevenue are placeholders (0) for now because
 * Order/Payment tables belong to Konara's and Peramuna's modules. Once the
 * team merges their entities into the shared database, inject their
 * repositories into ReportService and replace the placeholders with real
 * queries against the orders/payments tables.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummary {
    private long totalUsers;
    private long totalCustomers;
    private long totalVendors;
    private long totalAdmins;
    private long activeAccounts;
    private long deactivatedAccounts;
    private long suspendedAccounts;

    // Placeholders until Order/Payment modules are merged in
    private long totalOrders;
    private double totalRevenue;
}
