package org.jume.loyalitybot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.PosterApiConfig;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.service.BirthdayNotificationService;
import org.jume.loyalitybot.service.PosterApiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/birthdays")
@RequiredArgsConstructor
@Slf4j
public class AdminBirthdayController {

    private final BirthdayNotificationService birthdayNotificationService;
    private final PosterApiService posterApiService;
    private final PosterApiConfig posterApiConfig;

    @GetMapping
    public String birthdays(Model model, HttpSession session) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "birthdays");
        // Poster is the source of truth: the bonus lives on the client group, not in our config
        model.addAttribute("birthdayBonus", posterApiService.getClientGroupBirthdayBonuses()
                .getOrDefault(posterApiConfig.getDefaultClientGroupId(), java.math.BigDecimal.ZERO));

        List<PosterClientDto> clients = birthdayNotificationService.findTodaysBirthdayClients();
        Map<Long, Boolean> willBeGreeted = new LinkedHashMap<>();
        for (PosterClientDto client : clients) {
            willBeGreeted.put(client.getClientId(), birthdayNotificationService.willBeGreetedToday(client));
        }

        model.addAttribute("clients", clients);
        model.addAttribute("willBeGreeted", willBeGreeted);

        return "admin/birthdays/status";
    }

    /**
     * Sends the greeting text to the admin's own chat. No bonus is granted
     * and nothing is recorded, so it can be run as often as needed.
     */
    @PostMapping("/test")
    public String sendTest(HttpSession session, RedirectAttributes redirectAttributes) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        if (admin == null) {
            redirectAttributes.addFlashAttribute("error", "Сесія втрачена, увійдіть ще раз");
            return "redirect:/admin/birthdays";
        }

        log.info("Admin {} requested a test birthday greeting", admin.getId());

        String name = admin.getFirstName() != null ? admin.getFirstName() : "друже";
        if (birthdayNotificationService.sendTestGreeting(admin.getId(), name)) {
            redirectAttributes.addFlashAttribute("success",
                    "Тестове вітання надіслано у ваш чат з ботом. Бонуси не нараховувались.");
        } else {
            redirectAttributes.addFlashAttribute("error",
                    "Не вдалося надіслати. Напишіть боту /start і спробуйте ще раз.");
        }

        return "redirect:/admin/birthdays";
    }

    /**
     * Runs the real greeting job now instead of waiting for 9:00.
     * Safe to press twice — every customer is greeted at most once per year.
     */
    @PostMapping("/run")
    public String runNow(HttpSession session, RedirectAttributes redirectAttributes) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        log.info("Admin {} triggered birthday greetings manually", admin != null ? admin.getId() : "unknown");

        try {
            birthdayNotificationService.greetBirthdayCustomers();
            redirectAttributes.addFlashAttribute("success",
                    "Розсилку вітань виконано. Підсумок бот надіслав окремим повідомленням.");
        } catch (Exception e) {
            log.error("Error during manual birthday greetings", e);
            redirectAttributes.addFlashAttribute("error", "Помилка розсилки: " + e.getMessage());
        }

        return "redirect:/admin/birthdays";
    }
}
