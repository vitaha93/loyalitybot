package org.jume.loyalitybot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.dto.admin.TransactionDto;
import org.jume.loyalitybot.service.AdminStatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/transactions")
@RequiredArgsConstructor
@Slf4j
public class AdminTransactionController {

    private final AdminStatsService adminStatsService;

    @GetMapping
    public String listTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Model model,
            HttpSession session) {

        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "transactions");

        // Default to last 7 days if no dates specified
        if (dateFrom == null) {
            dateFrom = LocalDate.now().minusDays(7);
        }
        if (dateTo == null) {
            dateTo = LocalDate.now();
        }

        List<TransactionDto> transactions;
        if (clientId != null) {
            transactions = adminStatsService.getClientTransactions(clientId, size);
        } else {
            transactions = adminStatsService.getTransactions(dateFrom, dateTo, page, size);
        }

        model.addAttribute("transactions", transactions);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("clientId", clientId);
        model.addAttribute("currentPage", page);

        return "admin/transactions/list";
    }

    @GetMapping("/_table")
    public String transactionsTable(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Model model) {

        if (dateFrom == null) {
            dateFrom = LocalDate.now().minusDays(7);
        }
        if (dateTo == null) {
            dateTo = LocalDate.now();
        }

        List<TransactionDto> transactions;
        if (clientId != null) {
            transactions = adminStatsService.getClientTransactions(clientId, size);
        } else {
            transactions = adminStatsService.getTransactions(dateFrom, dateTo, page, size);
        }

        model.addAttribute("transactions", transactions);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("clientId", clientId);
        model.addAttribute("currentPage", page);

        return "admin/transactions/_table";
    }
}
