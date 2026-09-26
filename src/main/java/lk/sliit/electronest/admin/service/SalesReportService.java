package lk.sliit.electronest.admin.service;

import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.payment.repository.PaymentRepository;
import lk.sliit.electronest.payment.model.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TreeMap;

@Service
public class SalesReportService {
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final UserRepository users;

    public SalesReportService(PaymentRepository payments, OrderRepository orders, UserRepository users) {
        this.payments = payments;
        this.orders = orders;
        this.users = users;
    }

    public record Totals(BigDecimal sales, long successfulPayments, long orders, long accounts) {}
    public record Day(LocalDate date, Totals totals) {}
    public record Report(LocalDate from, LocalDate until, LocalDate previousFrom, LocalDate previousUntil,
                         Totals current, Totals previous, BigDecimal salesGrowth, BigDecimal accountGrowth,
                         List<Day> days) {}

    @Transactional(readOnly = true)
    public Report generate(LocalDate from, LocalDate until) {
        if (from == null || until == null || from.isBefore(LocalDate.of(1970, 1, 1))
                || until.isAfter(LocalDate.now()) || until.isBefore(from)
                || ChronoUnit.DAYS.between(from, until) >= 366) {
            throw new IllegalArgumentException("Choose a range of 1–366 days, from 1970 through today, with the start before the end.");
        }
        long length = ChronoUnit.DAYS.between(from, until) + 1;
        LocalDate previousFrom = from.minusDays(length);
        var days = new TreeMap<LocalDate, Totals>();
        previousFrom.datesUntil(until.plusDays(1)).forEach(date -> days.put(date, new Totals(BigDecimal.ZERO, 0, 0, 0)));
        var start = previousFrom.atStartOfDay();
        var end = until.plusDays(1).atStartOfDay();
        // Reporting dates follow the persisted server-local creation timestamp; statuses are current.
        for (var payment : payments.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end)) {
            if (payment.getPaymentStatus() != PaymentStatus.SUCCESSFUL) continue;
            LocalDate date = payment.getCreatedAt().toLocalDate();
            Totals t = days.get(date);
            days.put(date, new Totals(t.sales().add(payment.getAmount()), t.successfulPayments() + 1, t.orders(), t.accounts()));
        }
        for (var order : orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end)) {
            LocalDate date = order.getCreatedAt().toLocalDate();
            Totals t = days.get(date);
            days.put(date, new Totals(t.sales(), t.successfulPayments(), t.orders() + 1, t.accounts()));
        }
        for (var user : users.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end)) {
            LocalDate date = user.getCreatedAt().toLocalDate();
            Totals t = days.get(date);
            days.put(date, new Totals(t.sales(), t.successfulPayments(), t.orders(), t.accounts() + 1));
        }
        Totals current = total(days.tailMap(from).values());
        Totals previous = total(days.headMap(from).values());
        return new Report(from, until, previousFrom, from.minusDays(1), current, previous,
                growth(current.sales(), previous.sales()),
                growth(BigDecimal.valueOf(current.accounts()), BigDecimal.valueOf(previous.accounts())),
                days.tailMap(from).entrySet().stream().map(e -> new Day(e.getKey(), e.getValue())).toList());
    }

    private Totals total(java.util.Collection<Totals> values) {
        return values.stream().reduce(new Totals(BigDecimal.ZERO, 0, 0, 0), (a,b) ->
                new Totals(a.sales().add(b.sales()), a.successfulPayments()+b.successfulPayments(), a.orders()+b.orders(), a.accounts()+b.accounts()));
    }

    private BigDecimal growth(BigDecimal current, BigDecimal previous) {
        return previous.signum() == 0 ? null : current.subtract(previous).multiply(new BigDecimal("100"))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    public String csv(Report report) {
        // Only generated dates, fixed labels and numeric aggregates: no user-controlled CSV cells.
        StringBuilder csv = new StringBuilder("Period,From,Until,Successful payment value LKR,Successful payments,Orders placed,New accounts\r\n");
        append(csv, "Selected", report.from(), report.until(), report.current());
        append(csv, "Previous", report.previousFrom(), report.previousUntil(), report.previous());
        csv.append("\r\nDate,Successful payment value LKR,Successful payments,Orders placed,New accounts\r\n");
        for (Day day : report.days()) csv.append(day.date()).append(',').append(values(day.totals())).append("\r\n");
        return csv.toString();
    }
    private void append(StringBuilder csv, String label, LocalDate from, LocalDate until, Totals t) {
        csv.append(label).append(',').append(from).append(',').append(until).append(',').append(values(t)).append("\r\n");
    }
    private String values(Totals t) {
        return t.sales().toPlainString()+","+t.successfulPayments()+","+t.orders()+","+t.accounts();
    }
}
