package lk.sliit.electronest.admin.service;

import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.payment.model.*;
import lk.sliit.electronest.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesReportServiceTest {
    final PaymentRepository payments = mock(PaymentRepository.class);
    final OrderRepository orders = mock(OrderRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final SalesReportService service = new SalesReportService(payments, orders, users);
    final LocalDate from = LocalDate.of(2026, 1, 3), until = LocalDate.of(2026, 1, 4);

    @Test void comparesEqualPeriodsAndExcludesUnsuccessfulPayments() {
        when(payments.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from.minusDays(2).atStartOfDay(), until.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(payment(1, "100.00", PaymentStatus.SUCCESSFUL), payment(3, "125.50", PaymentStatus.SUCCESSFUL),
                        payment(4, "24.50", PaymentStatus.SUCCESSFUL), payment(4, "900", PaymentStatus.REFUNDED),
                        payment(3, "900", PaymentStatus.PENDING), payment(3, "900", PaymentStatus.FAILED)));
        User earlier = new User(); earlier.setCreatedAt(from.minusDays(1).atStartOfDay());
        User current = new User(); current.setCreatedAt(from.atStartOfDay());
        when(users.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(List.of(earlier,current));
        Order order = new Order(); order.setCreatedAt(until.atTime(23,59,59));
        when(orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(),any())).thenReturn(List.of(order));
        var report = service.generate(from, until);
        assertEquals(new BigDecimal("150.00"), report.current().sales());
        assertEquals(new BigDecimal("50.00"), report.salesGrowth());
        assertEquals(2, report.current().successfulPayments());
        assertEquals(1, report.current().orders());
        assertEquals(new BigDecimal("0.00"), report.accountGrowth());
        assertEquals(2, report.days().size());
        assertTrue(service.csv(report).contains("Selected,2026-01-03,2026-01-04,150.00,2,1,1"));
        assertTrue(service.csv(report).contains("Previous,2026-01-01,2026-01-02,100.00,1,0,1"));
    }
    @Test void emptyDaysAndZeroBaselineAreExplicit() {
        var report = service.generate(from, until);
        assertNull(report.salesGrowth());
        assertNull(report.accountGrowth());
        assertEquals(2, report.days().size());
        assertEquals(0, report.current().sales().signum());
    }
    @Test void reversedOversizedAndFutureRangesAreRejectedBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () -> service.generate(until, from));
        assertThrows(IllegalArgumentException.class, () -> service.generate(from.minusYears(2), until));
        assertThrows(IllegalArgumentException.class, () -> service.generate(LocalDate.now(), LocalDate.now().plusDays(1)));
        verifyNoInteractions(payments, orders, users);
    }
    private Payment payment(int day,String amount,PaymentStatus status) {
        Payment p = new Payment(); p.setCreatedAt(LocalDate.of(2026,1,day).atStartOfDay());
        p.setAmount(new BigDecimal(amount)); p.setPaymentStatus(status); return p;
    }
}
