package org.jume.loyalitybot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.model.ChatMessage;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.service.AdminStatsService;
import org.jume.loyalitybot.service.ChatService;
import org.jume.loyalitybot.service.CustomerService;
import org.jume.loyalitybot.service.PosterApiService;
import org.jume.loyalitybot.service.TelegramBotService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/customers")
@RequiredArgsConstructor
@Slf4j
public class AdminCustomerController {

    private final CustomerService customerService;
    private final PosterApiService posterApiService;
    private final AdminStatsService adminStatsService;
    private final TelegramBotService telegramBotService;
    private final ChatService chatService;

    @GetMapping
    public String listCustomers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            Model model,
            HttpSession session) {

        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "customers");

        Sort.Direction direction = order.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sort));

        Page<Customer> customers;
        if (search != null && !search.isBlank()) {
            customers = customerService.searchCustomers(search, pageRequest);
        } else {
            customers = customerService.getAllCustomers(pageRequest);
        }

        model.addAttribute("customers", customers);
        model.addAttribute("search", search);
        model.addAttribute("currentPage", page);
        model.addAttribute("sort", sort);
        model.addAttribute("order", order);

        return "admin/customers/list";
    }

    @GetMapping("/_table")
    public String customersTable(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            Model model) {

        Sort.Direction direction = order.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sort));

        Page<Customer> customers;
        if (search != null && !search.isBlank()) {
            customers = customerService.searchCustomers(search, pageRequest);
        } else {
            customers = customerService.getAllCustomers(pageRequest);
        }

        model.addAttribute("customers", customers);
        model.addAttribute("search", search);
        model.addAttribute("currentPage", page);
        model.addAttribute("sort", sort);
        model.addAttribute("order", order);

        return "admin/customers/_table";
    }

    @GetMapping("/{id}")
    public String customerDetail(@PathVariable Long id, Model model, HttpSession session) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "customers");

        Optional<Customer> customerOpt = customerService.getCustomerById(id);
        if (customerOpt.isEmpty()) {
            return "redirect:/admin/customers?error=notfound";
        }

        Customer customer = customerOpt.get();
        model.addAttribute("customer", customer);

        // Get Poster client data if linked
        if (customer.getPosterClientId() != null) {
            posterApiService.getClient(customer.getPosterClientId())
                .ifPresent(posterClient -> {
                    model.addAttribute("posterClient", posterClient);
                });

            // Get transactions for this client
            model.addAttribute("transactions",
                adminStatsService.getClientTransactions(customer.getPosterClientId(), 20));

            // Get favorite products
            model.addAttribute("favoriteProducts",
                adminStatsService.getClientFavoriteProducts(customer.getPosterClientId(), 5));
        }

        // Get chat messages and mark them as read
        List<ChatMessage> chatMessages = chatService.getCustomerChatMessages(id);
        model.addAttribute("chatMessages", chatMessages);
        chatService.markMessagesAsRead(id);

        return "admin/customers/detail";
    }

    @PostMapping("/{id}/send-message")
    public String sendMessage(
            @PathVariable Long id,
            @RequestParam String message,
            RedirectAttributes redirectAttributes) {

        Optional<Customer> customerOpt = customerService.getCustomerById(id);
        if (customerOpt.isEmpty()) {
            return "redirect:/admin/customers?error=notfound";
        }

        Customer customer = customerOpt.get();
        try {
            telegramBotService.sendMessage(customer.getTelegramId(), message);
            log.info("Admin sent message to customer {} (telegramId={})", id, customer.getTelegramId());
            redirectAttributes.addFlashAttribute("messageSent", true);
        } catch (Exception e) {
            log.error("Failed to send message to customer {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("messageError", e.getMessage());
        }

        return "redirect:/admin/customers/" + id;
    }

    @PostMapping("/{id}/chat")
    public String sendChatMessage(
            @PathVariable Long id,
            @RequestParam String message,
            RedirectAttributes redirectAttributes,
            HttpSession session) {

        Optional<Customer> customerOpt = customerService.getCustomerById(id);
        if (customerOpt.isEmpty()) {
            return "redirect:/admin/customers?error=notfound";
        }

        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        Long adminTelegramId = admin != null ? admin.getId() : 0L;

        Customer customer = customerOpt.get();
        try {
            boolean success = chatService.sendAdminMessage(customer, message, adminTelegramId);
            if (success) {
                log.info("Admin {} sent chat message to customer {} (telegramId={})",
                        adminTelegramId, id, customer.getTelegramId());
                redirectAttributes.addFlashAttribute("chatSent", true);
            } else {
                redirectAttributes.addFlashAttribute("chatError", "Не вдалося надіслати повідомлення");
            }
        } catch (Exception e) {
            log.error("Failed to send chat message to customer {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("chatError", e.getMessage());
        }

        return "redirect:/admin/customers/" + id + "#chat";
    }
}
