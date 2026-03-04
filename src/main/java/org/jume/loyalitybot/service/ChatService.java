package org.jume.loyalitybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.AdminConfig;
import org.jume.loyalitybot.model.ChatMessage;
import org.jume.loyalitybot.model.ChatMessage.SenderType;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.repository.ChatMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final TelegramBotService telegramBotService;
    private final AdminConfig adminConfig;

    /**
     * Process a message from a customer and forward it to all admins.
     */
    @Transactional
    public void processCustomerMessage(Customer customer, String messageText, Long telegramMessageId) {
        log.info("Processing customer message from {} (ID: {})", customer.getDisplayName(), customer.getId());

        // Save the message to DB
        ChatMessage message = ChatMessage.builder()
                .customer(customer)
                .senderType(SenderType.CUSTOMER)
                .senderTelegramId(customer.getTelegramId())
                .messageText(messageText)
                .telegramMessageId(telegramMessageId)
                .build();

        chatMessageRepository.save(message);

        // Format message for admins
        String adminMessage = formatCustomerMessageForAdmin(customer, messageText);

        // Forward to all admins
        for (Long adminId : adminConfig.getAdminTelegramIds()) {
            try {
                Long forwardedMsgId = telegramBotService.sendMessageWithId(adminId, adminMessage);
                if (forwardedMsgId != null) {
                    // Update the message with forwarding info (use the first admin's message ID)
                    if (message.getForwardedMessageId() == null) {
                        message.setForwardedMessageId(forwardedMsgId);
                        message.setAdminChatId(adminId);
                        chatMessageRepository.save(message);
                    }
                }
                log.debug("Forwarded customer message to admin {}", adminId);
            } catch (Exception e) {
                log.error("Failed to forward message to admin {}: {}", adminId, e.getMessage());
            }
        }
    }

    /**
     * Process a reply from an admin to a customer.
     */
    @Transactional
    public boolean processAdminReply(Long adminId, Long replyToMessageId, Long adminChatId, String replyText) {
        log.info("Processing admin reply from {} to message {}", adminId, replyToMessageId);

        // Find the original customer message by forwarded message ID
        Optional<ChatMessage> originalMessageOpt = chatMessageRepository
                .findByForwardedMessageIdAndAdminChatId(replyToMessageId, adminChatId);

        if (originalMessageOpt.isEmpty()) {
            log.warn("Could not find original message for reply. forwardedMsgId={}, adminChatId={}",
                    replyToMessageId, adminChatId);
            return false;
        }

        ChatMessage originalMessage = originalMessageOpt.get();
        Customer customer = originalMessage.getCustomer();

        // Save admin's reply
        ChatMessage adminReply = ChatMessage.builder()
                .customer(customer)
                .senderType(SenderType.ADMIN)
                .senderTelegramId(adminId)
                .messageText(replyText)
                .build();

        chatMessageRepository.save(adminReply);

        // Send reply to customer
        try {
            String customerMessage = formatAdminReplyForCustomer(replyText);
            Long sentMsgId = telegramBotService.sendMessageWithId(customer.getTelegramId(), customerMessage);
            adminReply.setTelegramMessageId(sentMsgId);
            chatMessageRepository.save(adminReply);

            log.info("Admin {} replied to customer {} (ID: {})", adminId, customer.getDisplayName(), customer.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to send admin reply to customer {}: {}", customer.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Send a message from admin panel to customer.
     */
    @Transactional
    public boolean sendAdminMessage(Customer customer, String messageText, Long adminTelegramId) {
        log.info("Admin {} sending message to customer {} from panel", adminTelegramId, customer.getId());

        // Save the message
        ChatMessage message = ChatMessage.builder()
                .customer(customer)
                .senderType(SenderType.ADMIN)
                .senderTelegramId(adminTelegramId)
                .messageText(messageText)
                .build();

        chatMessageRepository.save(message);

        // Send to customer
        try {
            String customerMessage = formatAdminReplyForCustomer(messageText);
            Long sentMsgId = telegramBotService.sendMessageWithId(customer.getTelegramId(), customerMessage);
            message.setTelegramMessageId(sentMsgId);
            chatMessageRepository.save(message);
            return true;
        } catch (Exception e) {
            log.error("Failed to send message to customer {}: {}", customer.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Get chat history for a customer.
     */
    public Page<ChatMessage> getCustomerChatHistory(Long customerId, Pageable pageable) {
        return chatMessageRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    /**
     * Get all chat messages for a customer (for display in admin panel).
     */
    public List<ChatMessage> getCustomerChatMessages(Long customerId) {
        return chatMessageRepository.findByCustomerIdOrderByCreatedAtAsc(customerId);
    }

    /**
     * Mark customer messages as read.
     */
    @Transactional
    public void markMessagesAsRead(Long customerId) {
        int updated = chatMessageRepository.markMessagesAsRead(
                customerId, SenderType.CUSTOMER, LocalDateTime.now());
        if (updated > 0) {
            log.debug("Marked {} messages as read for customer {}", updated, customerId);
        }
    }

    /**
     * Get count of unread customer messages.
     */
    public long getUnreadCustomerMessageCount() {
        return chatMessageRepository.countBySenderTypeAndReadAtIsNull(SenderType.CUSTOMER);
    }

    private String formatCustomerMessageForAdmin(Customer customer, String messageText) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>Повідомлення від клієнта</b>\n\n");
        sb.append("<b>Клієнт:</b> ");
        if (customer.getTelegramUsername() != null) {
            sb.append("@").append(customer.getTelegramUsername());
        } else {
            sb.append(customer.getDisplayName());
        }
        sb.append(" (ID: ").append(customer.getId()).append(")\n");

        if (customer.getPhone() != null) {
            sb.append("<b>Телефон:</b> +").append(customer.getPhone()).append("\n");
        }

        sb.append("\n<b>Повідомлення:</b>\n");
        sb.append(escapeHtml(messageText));
        sb.append("\n\n<i>Відповідайте на це повідомлення, щоб надіслати відповідь клієнту.</i>");

        return sb.toString();
    }

    private String formatAdminReplyForCustomer(String replyText) {
        return "<b>Відповідь від підтримки:</b>\n\n" + escapeHtml(replyText);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
