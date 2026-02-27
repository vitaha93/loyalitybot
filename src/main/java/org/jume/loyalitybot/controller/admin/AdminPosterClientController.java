package org.jume.loyalitybot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.service.AdminStatsService;
import org.jume.loyalitybot.service.CustomerService;
import org.jume.loyalitybot.service.PosterApiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/poster-clients")
@RequiredArgsConstructor
@Slf4j
public class AdminPosterClientController {

    private final PosterApiService posterApiService;
    private final CustomerService customerService;
    private final AdminStatsService adminStatsService;

    @GetMapping
    public String listPosterClients(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "totalPayedSum") String sort,
            @RequestParam(defaultValue = "desc") String order,
            Model model,
            HttpSession session) {

        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "poster-clients");

        List<PosterClientDto> allClients = posterApiService.getAllClients();

        // Filter by search query
        if (search != null && !search.isBlank()) {
            String searchLower = search.toLowerCase();
            allClients = allClients.stream()
                .filter(c ->
                    (c.getFirstName() != null && c.getFirstName().toLowerCase().contains(searchLower)) ||
                    (c.getLastName() != null && c.getLastName().toLowerCase().contains(searchLower)) ||
                    (c.getPhone() != null && c.getPhone().contains(search)) ||
                    (c.getPhoneNumber() != null && c.getPhoneNumber().contains(search)) ||
                    String.valueOf(c.getClientId()).contains(search)
                )
                .collect(Collectors.toList());
        }

        // Sort
        Comparator<PosterClientDto> comparator = switch (sort) {
            case "name" -> Comparator.comparing(
                c -> (c.getFirstName() != null ? c.getFirstName() : "") + (c.getLastName() != null ? c.getLastName() : ""),
                Comparator.nullsLast(String::compareToIgnoreCase)
            );
            case "bonus" -> Comparator.comparing(PosterClientDto::getBonusInHryvnia, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(PosterClientDto::getTotalPayedSumInHryvnia, Comparator.nullsLast(Comparator.naturalOrder()));
        };

        if ("desc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        }

        allClients = allClients.stream().sorted(comparator).collect(Collectors.toList());

        model.addAttribute("clients", allClients);
        model.addAttribute("search", search);
        model.addAttribute("sort", sort);
        model.addAttribute("order", order);

        return "admin/poster-clients/list";
    }

    @GetMapping("/{id}")
    public String posterClientDetail(@PathVariable Long id, Model model, HttpSession session) {
        TelegramLoginData admin = (TelegramLoginData) session.getAttribute("admin");
        model.addAttribute("admin", admin);
        model.addAttribute("activePage", "poster-clients");

        Optional<PosterClientDto> clientOpt = posterApiService.getClient(id);
        if (clientOpt.isEmpty()) {
            return "redirect:/admin/poster-clients?error=notfound";
        }

        PosterClientDto posterClient = clientOpt.get();
        model.addAttribute("posterClient", posterClient);

        // Check if there's a linked customer
        Optional<Customer> linkedCustomer = customerService.findByPosterClientId(id);
        linkedCustomer.ifPresent(customer -> model.addAttribute("linkedCustomer", customer));

        // Get transactions
        model.addAttribute("transactions", adminStatsService.getClientTransactions(id, 50));

        // Get favorite products
        model.addAttribute("favoriteProducts", adminStatsService.getClientFavoriteProducts(id, 10));

        return "admin/poster-clients/detail";
    }
}
