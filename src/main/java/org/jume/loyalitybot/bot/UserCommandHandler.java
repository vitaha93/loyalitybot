package org.jume.loyalitybot.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.LoyaltyConfig;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.service.BarcodeService;
import org.jume.loyalitybot.service.CustomerService;
import org.jume.loyalitybot.service.PosterApiService;
import org.jume.loyalitybot.service.TelegramBotService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCommandHandler {

    private final TelegramBotService telegramBotService;
    private final CustomerService customerService;
    private final BarcodeService barcodeService;
    private final PosterApiService posterApiService;
    private final LoyaltyConfig loyaltyConfig;

    public void handleBalance(Long chatId, Customer customer) {
        log.debug("Handling /balance for customer {}", customer.getTelegramId());

        if (customer.getPosterClientId() == null) {
            telegramBotService.sendMessage(chatId,
                    "На жаль, ваш акаунт не пов'язаний з системою лояльності. " +
                    "Зверніться до адміністратора.");
            return;
        }

        Optional<BigDecimal> balance = customerService.getCustomerBalance(customer.getTelegramId());

        if (balance.isPresent()) {
            String message = String.format(
                    "<b>Ваш баланс бонусів</b>\n\n" +
                    "<b>%s %s</b>\n\n" +
                    "1 бонус = 1 %s при оплаті",
                    balance.get().stripTrailingZeros().toPlainString(),
                    loyaltyConfig.getCurrencySymbol(),
                    loyaltyConfig.getCurrencySymbol()
            );
            telegramBotService.sendMessage(chatId, message);
        } else {
            telegramBotService.sendMessage(chatId,
                    "Не вдалося отримати баланс. Спробуйте пізніше.");
        }
    }

    public void handleCard(Long chatId, Customer customer) {
        log.debug("Handling /card for customer {}", customer.getTelegramId());

        if (customer.getPosterClientId() == null) {
            telegramBotService.sendMessage(chatId,
                    "На жаль, ваш акаунт не пов'язаний з системою лояльності. " +
                    "Зверніться до адміністратора.");
            return;
        }

        try {
            log.info("Fetching Poster client for ID: {}", customer.getPosterClientId());
            Optional<PosterClientDto> posterClient = posterApiService.getClient(customer.getPosterClientId());
            log.info("Poster client present: {}", posterClient.isPresent());

            if (posterClient.isEmpty()) {
                log.warn("Poster client not found for ID: {}", customer.getPosterClientId());
                telegramBotService.sendMessage(chatId,
                        "Не вдалося отримати дані клієнта. Спробуйте пізніше.");
                return;
            }

            String cardNumber = posterClient.get().getCardNumber();
            log.info("Card number from Poster: {}", cardNumber);

            if (cardNumber == null || cardNumber.isBlank()) {
                log.warn("Card number is null or blank for client: {}", customer.getPosterClientId());
                telegramBotService.sendMessage(chatId,
                        "Не вдалося отримати номер картки. Спробуйте пізніше.");
                return;
            }

            byte[] cardImage = barcodeService.generateLoyaltyCard(
                    cardNumber,
                    customer.getDisplayName()
            );

            String caption = String.format(
                    "Ваша картка лояльності\n" +
                    "Картка: %s\n\n" +
                    "Покажіть цей QR-код касиру для нарахування бонусів",
                    cardNumber
            );

            telegramBotService.sendPhoto(chatId, cardImage, caption);
        } catch (Exception e) {
            log.error("Error generating card for customer {}", customer.getTelegramId(), e);
            telegramBotService.sendMessage(chatId,
                    "Не вдалося згенерувати картку. Спробуйте пізніше.");
        }
    }

    public void handleHelp(Long chatId) {
        String helpMessage = """
                <b>Допомога</b>

                Доступні команди:

                /start - почати роботу з ботом
                /balance - перевірити баланс бонусів
                /card - показати картку лояльності
                /help - ця довідка

                <b>Як користуватися?</b>
                1. Зареєструйтеся, поділившись номером телефону
                2. Отримайте вітальний бонус
                3. Показуйте QR-код з /card при кожній покупці
                4. Накопичуйте бонуси та оплачуйте ними покупки!

                З питань звертайтеся до персоналу кав'ярні.""";

        telegramBotService.sendMessage(chatId, helpMessage);
    }
}
