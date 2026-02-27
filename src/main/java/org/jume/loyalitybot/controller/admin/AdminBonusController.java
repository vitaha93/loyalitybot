package org.jume.loyalitybot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.service.CustomerService;
import org.jume.loyalitybot.service.PosterApiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.Optional;

@Controller
@RequestMapping("/admin/bonuses")
@RequiredArgsConstructor
@Slf4j
public class AdminBonusController {

    private final CustomerService customerService;
    private final PosterApiService posterApiService;

    @GetMapping
    public String manageBonuses(Model model, HttpSession session) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "bonuses");
        return "admin/bonuses/manage";
    }

    @PostMapping("/add")
    public String addBonus(
            @RequestParam Long customerId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String comment,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");

        Optional<Customer> customerOpt = customerService.getCustomerById(customerId);
        if (customerOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Клієнта не знайдено");
            return "redirect:/admin/bonuses";
        }

        Customer customer = customerOpt.get();
        if (customer.getPosterClientId() == null) {
            redirectAttributes.addFlashAttribute("error", "Клієнт не прив'язаний до Poster");
            return "redirect:/admin/bonuses";
        }

        String bonusComment = String.format("Адмін %s: %s",
            admin != null ? admin.getDisplayName() : "Unknown",
            comment != null && !comment.isBlank() ? comment : "Ручне нарахування бонусів");

        boolean success = posterApiService.addBonus(customer.getPosterClientId(), amount, bonusComment);

        if (success) {
            log.info("Admin {} added {} bonus to customer {} (poster: {})",
                admin != null ? admin.getId() : "unknown",
                amount,
                customerId,
                customer.getPosterClientId());
            redirectAttributes.addFlashAttribute("success",
                String.format("Успішно нараховано %s бонусів клієнту %s", amount, customer.getDisplayName()));
        } else {
            redirectAttributes.addFlashAttribute("error", "Помилка при нарахуванні бонусів");
        }

        return "redirect:/admin/customers/" + customerId;
    }

    @PostMapping("/subtract")
    public String subtractBonus(
            @RequestParam Long customerId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String comment,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");

        Optional<Customer> customerOpt = customerService.getCustomerById(customerId);
        if (customerOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Клієнта не знайдено");
            return "redirect:/admin/bonuses";
        }

        Customer customer = customerOpt.get();
        if (customer.getPosterClientId() == null) {
            redirectAttributes.addFlashAttribute("error", "Клієнт не прив'язаний до Poster");
            return "redirect:/admin/bonuses";
        }

        // Subtract by adding negative amount
        BigDecimal negativeAmount = amount.negate();
        String bonusComment = String.format("Адмін %s: %s",
            admin != null ? admin.getDisplayName() : "Unknown",
            comment != null && !comment.isBlank() ? comment : "Ручне списання бонусів");

        boolean success = posterApiService.addBonus(customer.getPosterClientId(), negativeAmount, bonusComment);

        if (success) {
            log.info("Admin {} subtracted {} bonus from customer {} (poster: {})",
                admin != null ? admin.getId() : "unknown",
                amount,
                customerId,
                customer.getPosterClientId());
            redirectAttributes.addFlashAttribute("success",
                String.format("Успішно списано %s бонусів з клієнта %s", amount, customer.getDisplayName()));
        } else {
            redirectAttributes.addFlashAttribute("error", "Помилка при списанні бонусів");
        }

        return "redirect:/admin/customers/" + customerId;
    }

    @GetMapping("/search-customer")
    public String searchCustomer(@RequestParam String query, Model model) {
        var customers = customerService.searchCustomersForSelect(query, 10);
        model.addAttribute("customers", customers);
        return "admin/bonuses/_customer-results";
    }
}
