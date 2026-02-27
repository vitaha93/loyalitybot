package org.jume.loyalitybot.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class BarcodeService {

    private static final int QR_CODE_SIZE = 300;
    private static final int CARD_WIDTH = 400;
    private static final int CARD_HEIGHT = 500;
    private static final int PADDING = 20;

    public byte[] generateLoyaltyCard(String cardNumber, String customerName) {
        try {
            BufferedImage qrCode = generateQRCode(cardNumber);
            BufferedImage card = createLoyaltyCard(qrCode, cardNumber, customerName);
            return toByteArray(card);
        } catch (Exception e) {
            log.error("Error generating loyalty card for card {}", cardNumber, e);
            throw new RuntimeException("Failed to generate loyalty card", e);
        }
    }

    public byte[] generateQRCodeImage(String content) {
        try {
            BufferedImage qrCode = generateQRCode(content);
            return toByteArray(qrCode);
        } catch (Exception e) {
            log.error("Error generating QR code for content: {}", content, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    private BufferedImage generateQRCode(String content) throws WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE, hints);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }

    private BufferedImage createLoyaltyCard(BufferedImage qrCode, String cardNumber, String customerName) {
        BufferedImage card = new BufferedImage(CARD_WIDTH, CARD_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = card.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(139, 90, 43),
                CARD_WIDTH, CARD_HEIGHT, new Color(92, 51, 23)
        );
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        String title = "LOYALTY CARD";
        FontMetrics fm = g2d.getFontMetrics();
        int titleX = (CARD_WIDTH - fm.stringWidth(title)) / 2;
        g2d.drawString(title, titleX, PADDING + 30);

        int qrX = (CARD_WIDTH - QR_CODE_SIZE) / 2;
        int qrY = 80;
        g2d.drawImage(qrCode, qrX, qrY, null);

        g2d.setFont(new Font("Arial", Font.PLAIN, 16));
        fm = g2d.getFontMetrics();

        String name = truncateString(customerName, 30);
        int nameX = (CARD_WIDTH - fm.stringWidth(name)) / 2;
        g2d.drawString(name, nameX, qrY + QR_CODE_SIZE + 40);

        g2d.dispose();
        return card;
    }

    private String truncateString(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    private byte[] toByteArray(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}
