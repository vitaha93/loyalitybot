package org.jume.loyalitybot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.model.SyncStatus;
import org.jume.loyalitybot.repository.CustomerRepository;
import org.jume.loyalitybot.repository.SyncStatusRepository;
import org.jume.loyalitybot.service.PosterApiService;
import org.jume.loyalitybot.service.TransactionSyncService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.List;

@Controller
@RequestMapping("/admin/sync")
@RequiredArgsConstructor
@Slf4j
public class AdminSyncController {

    private final TransactionSyncService transactionSyncService;
    private final SyncStatusRepository syncStatusRepository;
    private final CustomerRepository customerRepository;
    private final PosterApiService posterApiService;

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

    @PostMapping("/phones")
    public String syncPhones(HttpSession session, RedirectAttributes redirectAttributes) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        log.info("Admin {} triggered phone sync to Poster", admin != null ? admin.getId() : "unknown");

        try {
            List<Customer> customers = customerRepository.findAll();
            int updated = 0;
            int skipped = 0;

            for (Customer customer : customers) {
                if (customer.getPosterClientId() == null || customer.getPhone() == null || customer.getPhone().isBlank()) {
                    continue;
                }

                PosterClientDto posterClient = posterApiService.getClient(customer.getPosterClientId()).orElse(null);
                if (posterClient == null) {
                    log.warn("Poster client {} not found for customer {}", customer.getPosterClientId(), customer.getId());
                    continue;
                }

                String posterPhone = posterClient.getNormalizedPhone();
                if (posterPhone == null || posterPhone.isBlank()) {
                    log.info("Updating phone for Poster client {}: {}", customer.getPosterClientId(), customer.getPhone());
                    if (posterApiService.updateClientPhone(customer.getPosterClientId(), customer.getPhone())) {
                        updated++;
                    }
                } else {
                    skipped++;
                }
            }

            redirectAttributes.addFlashAttribute("success",
                String.format("Синхронізацію телефонів завершено. Оновлено: %d, пропущено (вже є): %d", updated, skipped));
        } catch (Exception e) {
            log.error("Error during phone sync", e);
            redirectAttributes.addFlashAttribute("error", "Помилка синхронізації телефонів: " + e.getMessage());
        }

        return "redirect:/admin/sync";
    }
}
