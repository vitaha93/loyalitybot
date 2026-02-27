package org.jume.loyalitybot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.model.SyncStatus;
import org.jume.loyalitybot.repository.SyncStatusRepository;
import org.jume.loyalitybot.service.TransactionSyncService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin/sync")
@RequiredArgsConstructor
@Slf4j
public class AdminSyncController {

    private final TransactionSyncService transactionSyncService;
    private final SyncStatusRepository syncStatusRepository;

    @GetMapping
    public String syncStatus(Model model, HttpSession session) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "sync");

        SyncStatus status = syncStatusRepository.findBySyncType(SyncStatus.TRANSACTIONS).orElse(null);
        model.addAttribute("syncStatus", status);

        return "admin/sync/status";
    }

    @PostMapping("/transactions")
    public String syncTransactions(HttpSession session, RedirectAttributes redirectAttributes) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        log.info("Admin {} triggered manual transaction sync", admin != null ? admin.getId() : "unknown");

        try {
            int synced = transactionSyncService.syncTransactions();
            redirectAttributes.addFlashAttribute("success",
                String.format("Синхронізацію завершено. Синхронізовано %d нових транзакцій", synced));
        } catch (Exception e) {
            log.error("Error during manual sync", e);
            redirectAttributes.addFlashAttribute("error", "Помилка синхронізації: " + e.getMessage());
        }

        return "redirect:/admin/sync";
    }
}
