package fr.miage.collectivit.salesservice.service.receipt;

import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.model.Order;
import fr.miage.collectivit.salesservice.repository.OrderRepository;
import fr.miage.collectivit.salesservice.service.OrderProcessingService;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing automated email delivery of purchase proofs and Click & Collect receipts with
 * PDF attachment. Supports asynchronous transmission and resilient fallback if SMTP server is
 * unreachable.
 */
@Slf4j
@Service
public class ReceiptEmailService {

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
  private final ReceiptPdfService receiptPdfService;
  private final OrderRepository orderRepository;
  private final OrderProcessingService orderProcessingService;
  private final String fromEmail;

  @Autowired
  public ReceiptEmailService(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      ReceiptPdfService receiptPdfService,
      OrderRepository orderRepository,
      OrderProcessingService orderProcessingService,
      @Value("${spring.mail.from:contact@shop-loc.fr}") String fromEmail) {
    this.mailSenderProvider = mailSenderProvider;
    this.receiptPdfService = receiptPdfService;
    this.orderRepository = orderRepository;
    this.orderProcessingService = orderProcessingService;
    this.fromEmail = fromEmail;
  }

  /**
   * Asynchronously generates and sends the PDF receipt to the customer's email upon order payment.
   *
   * @param orderIdentifier reference, UID, or ID of the paid order
   */
  @Async
  @Transactional
  public void sendReceiptEmailAsync(String orderIdentifier) {
    try {
      Optional<Order> orderOpt = orderProcessingService.resolveOrder(orderIdentifier);
      if (orderOpt.isEmpty()) {
        log.warn("Cannot send receipt email: order {} not found", orderIdentifier);
        return;
      }

      Order order = orderOpt.get();
      if (order.getReceiptSentAt() != null) {
        log.info(
            "Receipt email already dispatched for order {} at {}",
            order.getOrderReference(),
            order.getReceiptSentAt());
        return;
      }

      OrderResponse orderResponse = orderProcessingService.mapToResponse(order);
      boolean sent = sendReceiptEmailInternal(orderResponse);
      if (sent) {
        order.setReceiptSentAt(Instant.now());
        orderRepository.save(order);
        log.info(
            "Receipt email successfully dispatched for order {} to {}",
            order.getOrderReference(),
            order.getCustomerEmail());
      }
    } catch (Exception ex) {
      log.error(
          "Failed to send receipt email for order {}: {}", orderIdentifier, ex.getMessage(), ex);
    }
  }

  /**
   * Allows manual resending of the receipt email upon user request from customer account.
   *
   * @param orderIdentifier reference or UID of the order
   * @return true if email was dispatched or queued, false otherwise
   */
  @Transactional
  public boolean resendReceiptEmail(String orderIdentifier) {
    Optional<Order> orderOpt = orderProcessingService.resolveOrder(orderIdentifier);
    if (orderOpt.isEmpty()) {
      return false;
    }
    Order order = orderOpt.get();
    OrderResponse orderResponse = orderProcessingService.mapToResponse(order);
    boolean success = sendReceiptEmailInternal(orderResponse);
    if (success) {
      order.setReceiptSentAt(Instant.now());
      orderRepository.save(order);
    }
    return success;
  }

  private boolean sendReceiptEmailInternal(OrderResponse order) {
    String recipient = order.customerEmail();
    if (recipient == null || recipient.isBlank()) {
      log.warn(
          "No recipient email associated with order {}, skipping email transmission",
          order.orderReference());
      return false;
    }

    byte[] pdfBytes = receiptPdfService.generateReceiptPdf(order);
    JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

    if (mailSender == null) {
      log.info(
          "JavaMailSender is not configured. Simulated receipt email to {} for order {} with {} bytes PDF attachment",
          recipient,
          order.orderReference(),
          pdfBytes.length);
      return true; // Mark as delivered in mock / test mode
    }

    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

      helper.setFrom(fromEmail);
      helper.setTo(recipient.trim());
      helper.setSubject(
          "Confirmation et preuve d'achat de votre commande ShopLoc - " + order.orderReference());

      String emailBody = buildHtmlEmailBody(order);
      helper.setText(emailBody, true);

      String pdfFilename = "preuve-achat-" + order.orderReference() + ".pdf";
      helper.addAttachment(pdfFilename, new ByteArrayResource(pdfBytes), "application/pdf");

      mailSender.send(mimeMessage);
      return true;
    } catch (Exception ex) {
      log.error(
          "Error occurred while transmitting email via SMTP for order {}: {}",
          order.orderReference(),
          ex.getMessage());
      return false;
    }
  }

  private String buildHtmlEmailBody(OrderResponse order) {
    String clientCode = order.clientCode() != null ? order.clientCode() : "N/A";
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <style>
            body { font-family: 'Segoe UI', Arial, sans-serif; color: #1e293b; background-color: #f8fafc; padding: 20px; }
            .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; border: 1px solid #e2e8f0; padding: 24px; }
            .header { text-align: center; border-bottom: 2px solid #10b981; padding-bottom: 16px; margin-bottom: 20px; }
            .badge { display: inline-block; background-color: #10b981; color: white; padding: 4px 12px; border-radius: 9999px; font-weight: bold; font-size: 12px; }
            .card { background-color: #f1f5f9; border-radius: 6px; padding: 16px; margin-bottom: 16px; }
            .footer { font-size: 11px; color: #64748b; text-align: center; margin-top: 24px; }
          </style>
        </head>
        <body>
          <div class="container">
            <div class="header">
              <h1 style="color: #0f172a; margin: 0;">ShopLoc</h1>
              <p style="color: #64748b; margin: 4px 0 0 0;">Confirmation de commande & Preuve d'achat</p>
            </div>
            <p>Bonjour,</p>
            <p>Nous vous confirmons la bonne réception du paiement pour votre commande <strong>%s</strong>.</p>
            <div class="card">
              <p style="margin: 4px 0;"><strong>Référence :</strong> %s</p>
              <p style="margin: 4px 0;"><strong>Montant réglé :</strong> %.2f €</p>
              <p style="margin: 4px 0;"><strong>Code fidélité client :</strong> <code>%s</code></p>
              <p style="margin: 4px 0;"><strong>Statut :</strong> <span class="badge">PAYÉE</span></p>
            </div>
            <div style="background-color: #ecfdf5; border-left: 4px solid #10b981; padding: 12px; margin-bottom: 20px;">
              <h4 style="margin: 0 0 6px 0; color: #065f46;">Consigne de retrait en boutique Click & Collect</h4>
              <p style="margin: 0; font-size: 13px; color: #047857;">
                Veuillez vous présenter chez chacun des commerçants partenaires muni de votre carte physique ShopLoc
                ou de votre QR Code dans l'application mobile afin de récupérer vos articles.
              </p>
            </div>
            <p>Votre <strong>preuve d'achat officielle</strong> est jointe à cet email au format PDF (incluant le détail par commerce et votre QR Code de retrait).</p>
            <p>Merci pour votre confiance et à très bientôt chez vos commerçants locaux !</p>
            <div class="footer">
              Cet email automatique a été généré par la plateforme ShopLoc Collectiv'IT. Merci de ne pas y répondre directement.
            </div>
          </div>
        </body>
        </html>
        """
        .formatted(order.orderReference(), order.orderReference(), order.totalAmount(), clientCode);
  }
}
