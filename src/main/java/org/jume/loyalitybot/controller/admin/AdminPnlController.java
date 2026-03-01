package org.jume.loyalitybot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.admin.PnlReportDto;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.service.PnlService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Controller
@RequestMapping("/admin/pnl")
@RequiredArgsConstructor
@Slf4j
public class AdminPnlController {

    private final PnlService pnlService;

    @GetMapping
    public String pnlReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "month") String period,
            Model model,
            HttpSession session) {

        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "pnl");

        // Calculate date range based on period or explicit dates
        LocalDate today = LocalDate.now();

        if (dateFrom == null || dateTo == null) {
            switch (period) {
                case "today" -> {
                    dateFrom = today;
                    dateTo = today;
                }
                case "week" -> {
                    dateFrom = today.minusDays(6);
                    dateTo = today;
                }
                case "month" -> {
                    dateFrom = today.withDayOfMonth(1);
                    dateTo = today;
                }
                case "last_month" -> {
                    dateFrom = today.minusMonths(1).withDayOfMonth(1);
                    dateTo = today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
                }
                case "quarter" -> {
                    int currentQuarter = (today.getMonthValue() - 1) / 3;
                    dateFrom = today.withMonth(currentQuarter * 3 + 1).withDayOfMonth(1);
                    dateTo = today;
                }
                case "year" -> {
                    dateFrom = today.withDayOfYear(1);
                    dateTo = today;
                }
                default -> {
                    dateFrom = today.withDayOfMonth(1);
                    dateTo = today;
                }
            }
        }

        PnlReportDto report = pnlService.generateReport(dateFrom, dateTo);

        model.addAttribute("report", report);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("period", period);

        // Prepare chart data as JSON
        model.addAttribute("chartLabels", report.getDailyData().stream()
                .map(d -> d.getDate().toString())
                .toList());
        model.addAttribute("chartRevenue", report.getDailyData().stream()
                .map(d -> d.getRevenue().doubleValue())
                .toList());
        model.addAttribute("chartExpenses", report.getDailyData().stream()
                .map(d -> d.getExpenses().doubleValue())
                .toList());
        model.addAttribute("chartProfit", report.getDailyData().stream()
                .map(d -> d.getProfit().doubleValue())
                .toList());

        return "admin/pnl/report";
    }
}
