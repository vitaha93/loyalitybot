package org.jume.loyalitybot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.model.BroadcastJob;
import org.jume.loyalitybot.service.BroadcastService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.List;

@Controller
@RequestMapping("/admin/broadcasts")
@RequiredArgsConstructor
@Slf4j
public class AdminBroadcastController {

    private final BroadcastService broadcastService;

    @GetMapping
    public String listBroadcasts(Model model, HttpSession session) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "broadcasts");

        List<BroadcastJob> broadcasts = broadcastService.getAllBroadcasts();
        model.addAttribute("broadcasts", broadcasts);

        return "admin/broadcasts/list";
    }

    @GetMapping("/create")
    public String createBroadcastForm(Model model, HttpSession session) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "broadcasts");
        return "admin/broadcasts/create";
    }

    @PostMapping("/create")
    public String createBroadcast(
            @RequestParam String message,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        if (admin == null) {
            return "redirect:/admin/login";
        }

        if (message == null || message.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Повідомлення не може бути порожнім");
            return "redirect:/admin/broadcasts/create";
        }

        BroadcastJob job = broadcastService.createBroadcast(message, admin.getId());
        log.info("Admin {} created broadcast job {}", admin.getId(), job.getId());

        redirectAttributes.addFlashAttribute("success",
            String.format("Розсилку #%d створено. Отримувачів: %d", job.getId(), job.getTotalRecipients()));

        return "redirect:/admin/broadcasts";
    }

    @GetMapping("/{id}/status")
    public String broadcastStatus(@PathVariable Long id, Model model) {
        BroadcastJob job = broadcastService.getBroadcastStatus(id);
        model.addAttribute("broadcast", job);
        return "admin/broadcasts/_status";
    }

    @GetMapping("/{id}")
    public String broadcastDetail(@PathVariable Long id, Model model, HttpSession session) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "broadcasts");

        BroadcastJob job = broadcastService.getBroadcastStatus(id);
        model.addAttribute("broadcast", job);

        return "admin/broadcasts/detail";
    }
}
