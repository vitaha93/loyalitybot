package org.jume.loyalitybot.controller.admin;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.AdminConfig;
import org.jume.loyalitybot.config.TelegramBotConfig;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.jume.loyalitybot.security.TelegramAuthenticationToken;
import org.jume.loyalitybot.service.AdminAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final TelegramBotConfig telegramConfig;
    private final AdminConfig adminConfig;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("botUsername", telegramConfig.getUsername());
        model.addAttribute("devMode", "local".equals(activeProfile));
        return "admin/login";
    }

    @GetMapping("/dev-login")
    public String devLogin(HttpSession session, RedirectAttributes redirectAttributes) {
        if (!"local".equals(activeProfile)) {
            redirectAttributes.addFlashAttribute("error", "Dev login доступний тільки в local профілі");
            return "redirect:/admin/login";
        }

        // Get first admin ID for dev login
        var adminIds = adminConfig.getAdminTelegramIds();
        if (adminIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Налаштуйте ADMIN_TELEGRAM_IDS для dev login");
            return "redirect:/admin/login";
        }

        Long adminId = adminIds.iterator().next();

        TelegramLoginData loginData = new TelegramLoginData();
        loginData.setId(adminId);
        loginData.setFirstName("Dev");
        loginData.setLastName("Admin");
        loginData.setUsername("dev_admin");

        TelegramAuthenticationToken auth = new TelegramAuthenticationToken(
            loginData,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        session.setAttribute("admin", loginData);

        log.info("Dev admin logged in with ID: {}", adminId);
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/auth/callback")
    public String handleTelegramCallback(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false, name = "first_name") String firstName,
            @RequestParam(required = false, name = "last_name") String lastName,
            @RequestParam(required = false) String username,
            @RequestParam(required = false, name = "photo_url") String photoUrl,
            @RequestParam(required = false, name = "auth_date") Long authDate,
            @RequestParam(required = false) String hash,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        TelegramLoginData loginData = new TelegramLoginData();
        loginData.setId(id);
        loginData.setFirstName(firstName);
        loginData.setLastName(lastName);
        loginData.setUsername(username);
        loginData.setPhotoUrl(photoUrl);
        loginData.setAuthDate(authDate);
        loginData.setHash(hash);

        log.info("Telegram login callback for user: {} ({})", loginData.getDisplayName(), id);

        if (!adminAuthService.verifyTelegramLogin(loginData)) {
            log.warn("Invalid Telegram login for user {}", id);
            redirectAttributes.addFlashAttribute("error", "Невдала автентифікація через Telegram");
            return "redirect:/admin/login";
        }

        if (!adminAuthService.isAdmin(id)) {
            log.warn("Non-admin user {} attempted to access admin panel", id);
            redirectAttributes.addFlashAttribute("error", "У вас немає прав доступу до адмін-панелі");
            return "redirect:/admin/login";
        }

        // Create authentication token
        TelegramAuthenticationToken auth = new TelegramAuthenticationToken(
            loginData,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Store in session
        session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        session.setAttribute("admin", loginData);

        log.info("Admin {} logged in successfully", loginData.getDisplayName());
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        SecurityContextHolder.clearContext();
        return "redirect:/admin/login";
    }
}
