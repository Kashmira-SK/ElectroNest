package lk.sliit.electronest.admin.controller;

import lk.sliit.electronest.admin.service.ReportService;
import lk.sliit.electronest.admin.service.SalesReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;

@Controller
@RequestMapping("/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class SalesReportController {
    private final SalesReportService sales;
    private final ReportService summary;
    public SalesReportController(SalesReportService sales, ReportService summary) {
        this.sales = sales;
        this.summary = summary;
    }
    @GetMapping
    public String report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until, Model model) {
        until = until == null ? LocalDate.now() : until;
        from = from == null ? LocalDate.now().minusDays(29) : from;
        model.addAttribute("from", from);
        model.addAttribute("until", until);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("summary", summary.getDashboardSummary());
        try { model.addAttribute("salesReport", sales.generate(from, until)); }
        catch (IllegalArgumentException ex) { model.addAttribute("reportError", ex.getMessage()); }
        return "admin/reports";
    }
    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        try {
            return ResponseEntity.ok().header("Content-Type", "text/csv;charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=electronest-sales-" + from + "-" + until + ".csv")
                    .header("Cache-Control", "no-store")
                    .body(sales.csv(sales.generate(from, until)));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report date range");
        }
    }
}
