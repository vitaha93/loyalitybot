package org.jume.loyalitybot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.admin.DashboardStats;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.service.AdminStatsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardController {

    private final AdminStatsService adminStatsService;

    @GetMapping
    public String index() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession session) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "dashboard");

        DashboardStats stats = adminStatsService.getDashboardStats();
        model.addAttribute("stats", stats);

        model.addAttribute("topCustomers", adminStatsService.getTopCustomersBySpending(10));
        model.addAttribute("recentTransactions", adminStatsService.getRecentTransactions(10));

        return "admin/dashboard";
    }
}
