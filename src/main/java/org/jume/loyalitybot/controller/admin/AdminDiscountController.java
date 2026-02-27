package org.jume.loyalitybot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.model.PersonalDiscount;
import org.jume.loyalitybot.repository.PersonalDiscountRepository;
import org.jume.loyalitybot.service.CustomerService;
import org.jume.loyalitybot.service.PosterApiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/discounts")
@RequiredArgsConstructor
@Slf4j
public class AdminDiscountController {

    private final PersonalDiscountRepository discountRepository;
    private final CustomerService customerService;
    private final PosterApiService posterApiService;

    @GetMapping
    public String listDiscounts(Model model, HttpSession session) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "discounts");

        List<PersonalDiscount> activeDiscounts = discountRepository.findActiveDiscounts(LocalDate.now());
        List<PersonalDiscount> expiredDiscounts = discountRepository.findExpiredDiscounts(LocalDate.now());

        model.addAttribute("activeDiscounts", activeDiscounts);
        model.addAttribute("expiredDiscounts", expiredDiscounts);

        return "admin/discounts/list";
    }

    @GetMapping("/create")
    public String createDiscountForm(
            @RequestParam(required = false) Long customerId,
            Model model,
            HttpSession session) {

        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "discounts");

        if (customerId != null) {
            customerService.getCustomerById(customerId)
                .ifPresent(customer -> model.addAttribute("selectedCustomer", customer));
        }

        return "admin/discounts/create";
    }

    @PostMapping("/create")
    public String createDiscount(
            @RequestParam Long customerId,
            @RequestParam Integer discountPercent,
            @RequestParam(required = false) LocalDate validFrom,
            @RequestParam LocalDate validUntil,
            @RequestParam(required = false) String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");

        Optional<Customer> customerOpt = customerService.getCustomerById(customerId);
        if (customerOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Клієнта не знайдено");
            return "redirect:/admin/discounts/create";
        }

        Customer customer = customerOpt.get();

        if (discountPercent < 1 || discountPercent > 100) {
            redirectAttributes.addFlashAttribute("error", "Знижка має бути від 1% до 100%");
            return "redirect:/admin/discounts/create";
        }

        if (validFrom == null) {
            validFrom = LocalDate.now();
        }

        if (validUntil.isBefore(validFrom)) {
            redirectAttributes.addFlashAttribute("error", "Дата закінчення має бути після дати початку");
            return "redirect:/admin/discounts/create";
        }

        // Create personal discount record
        PersonalDiscount discount = PersonalDiscount.builder()
            .customer(customer)
            .discountPercent(discountPercent)
            .validFrom(validFrom)
            .validUntil(validUntil)
            .reason(reason)
            .createdBy(admin != null ? admin.getId() : null)
            .build();

        discountRepository.save(discount);

        // Apply discount in Poster if customer is linked
        if (customer.getPosterClientId() != null) {
            boolean success = posterApiService.setClientDiscount(customer.getPosterClientId(), discountPercent);
            if (!success) {
                log.warn("Failed to set discount in Poster for client {}", customer.getPosterClientId());
            }
        }

        log.info("Admin {} created {}% discount for customer {} until {}",
            admin != null ? admin.getId() : "unknown",
            discountPercent,
            customerId,
            validUntil);

        redirectAttributes.addFlashAttribute("success",
            String.format("Знижку %d%% для %s створено до %s", discountPercent, customer.getDisplayName(), validUntil));

        return "redirect:/admin/discounts";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivateDiscount(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");

        Optional<PersonalDiscount> discountOpt = discountRepository.findById(id);
        if (discountOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Знижку не знайдено");
            return "redirect:/admin/discounts";
        }

        PersonalDiscount discount = discountOpt.get();
        discount.setActive(false);
        discountRepository.save(discount);

        // Remove discount in Poster
        Customer customer = discount.getCustomer();
        if (customer.getPosterClientId() != null) {
            posterApiService.setClientDiscount(customer.getPosterClientId(), 0);
        }

        log.info("Admin {} deactivated discount {} for customer {}",
            admin != null ? admin.getId() : "unknown",
            id,
            customer.getId());

        redirectAttributes.addFlashAttribute("success", "Знижку деактивовано");
        return "redirect:/admin/discounts";
    }
}
