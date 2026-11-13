package fr.miage.collectivit.salesservice.service.receipt;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import fr.miage.collectivit.salesservice.dto.OrderItemResponse;
import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.dto.OrderStoreSummaryResponse;
import fr.miage.collectivit.salesservice.model.OrderStatus;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service generating official ShopLoc bon de retrait and click & collect receipt in PDF format.
 * Implements Story SLE-21 using programmatic OpenPDF rendering (safe from SSRF / XXE injection).
 */
@Slf4j
@Service
@SuppressWarnings({
  "PMD.AvoidInstantiatingObjectsInLoops",
  "PMD.LooseCoupling",
  "PMD.AvoidCatchingGenericException",
  "PMD.CloseResource",
  "PMD.AvoidUncheckedExceptionsInSignatures",
  "PMD.GuardLogStatement",
  "PMD.TooManyMethods",
  "PMD.CognitiveComplexity",
  "PMD.ExcessiveImports",
  "PMD.GodClass"
})
public class ReceiptPdfService {

  private static final Color PRIMARY_COLOR = new Color(15, 23, 42); // slate-900
  private static final Color ACCENT_COLOR = new Color(16, 185, 129); // emerald-500
  private static final Color TEXT_MUTED = new Color(100, 116, 139); // slate-500
  private static final Color TABLE_HEADER_BG = new Color(241, 245, 249); // slate-100
  private static final Color STORE_HEADER_BG = new Color(226, 232, 240); // slate-200

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Europe/Paris"));

  /**
   * Generates a byte array containing the official PDF receipt / bon de retrait for an order.
   *
   * @param order the completed order details
   * @return byte array representing the rendered PDF document
   */
  public byte[] generateReceiptPdf(OrderResponse order) {
    if (order == null) {
      throw new IllegalArgumentException("Order cannot be null");
    }

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4, 36, 36, 36, 36);
      PdfWriter.getInstance(document, out);
      document.open();

      boolean isCompleted = OrderStatus.COMPLETED.name().equalsIgnoreCase(order.status());
      document.addTitle(
          (isCompleted ? "Facture - " : "Bon de retrait - ") + order.orderReference());
      document.addAuthor("ShopLoc");
      document.addSubject(
          isCompleted
              ? "Facture & Justificatif d'achat Click & Collect"
              : "Bon de retrait & Reçu de commande Click & Collect");

      addTopHeaderWithQrCode(document, order);
      addStoreSummariesAndItems(document, order);
      addTotalsAndLoyalty(document, order);
      addFooter(document, isCompleted);

      document.close();
      return out.toByteArray();
    } catch (DocumentException ex) {
      log.error(
          "Failed to generate receipt PDF for order {}: {}",
          order.orderReference(),
          ex.getMessage(),
          ex);
      throw new IllegalStateException(
          "Erreur lors de la génération du document PDF de bon de retrait", ex);
    } catch (Exception ex) {
      log.error(
          "Unexpected error during PDF generation for order {}: {}",
          order.orderReference(),
          ex.getMessage(),
          ex);
      throw new IllegalStateException("Impossible de générer le reçu PDF", ex);
    }
  }

  private void addTopHeaderWithQrCode(Document document, OrderResponse order)
      throws DocumentException {
    PdfPTable headerTable = new PdfPTable(2);
    headerTable.setWidthPercentage(100);
    headerTable.setWidths(new float[] {6.2f, 3.8f});
    headerTable.setSpacingAfter(15);

    headerTable.addCell(createLeftHeaderCell(order));
    headerTable.addCell(createRightQrCell(order));
    document.add(headerTable);
  }

  private PdfPCell createLeftHeaderCell(OrderResponse order) {
    PdfPCell leftCell = new PdfPCell();
    leftCell.setBorder(PdfPCell.NO_BORDER);
    leftCell.setPaddingRight(10f);

    Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, PRIMARY_COLOR);
    Paragraph brandPara = new Paragraph("ShopLoc", brandFont);
    brandPara.setSpacingAfter(2);
    leftCell.addElement(brandPara);

    boolean isCompleted = OrderStatus.COMPLETED.name().equalsIgnoreCase(order.status());
    Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, ACCENT_COLOR);
    String headerTitle =
        isCompleted ? "FACTURE & JUSTIFICATIF D'ACHAT" : "BON DE RETRAIT & REÇU DE COMMANDE";
    Paragraph titlePara = new Paragraph(headerTitle, titleFont);
    titlePara.setSpacingAfter(8);
    leftCell.addElement(titlePara);

    Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, PRIMARY_COLOR);
    Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.DARK_GRAY);

    leftCell.addElement(new Paragraph("Commande : " + order.orderReference(), labelFont));
    String dateStr = order.createdAt() != null ? DATE_FORMATTER.format(order.createdAt()) : "N/A";
    leftCell.addElement(new Paragraph("Date d'achat : " + dateStr, valueFont));
    leftCell.addElement(new Paragraph("Statut : " + formatStatusFrench(order.status()), labelFont));

    if (isCompleted) {
      Instant recupDate = order.updatedAt() != null ? order.updatedAt() : order.createdAt();
      String dateRecupStr = recupDate != null ? DATE_FORMATTER.format(recupDate) : dateStr;
      leftCell.addElement(new Paragraph("Date de récupération : " + dateRecupStr, valueFont));
    }

    if (order.customerEmail() != null && !order.customerEmail().isBlank()) {
      leftCell.addElement(new Paragraph("Email client : " + order.customerEmail(), valueFont));
    }

    leftCell.addElement(createInstructionsParagraph(isCompleted));
    return leftCell;
  }

  private Paragraph createInstructionsParagraph(boolean isCompleted) {
    Font instructTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, PRIMARY_COLOR);
    Font instructFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7.5f, TEXT_MUTED);

    Paragraph instructP = new Paragraph();
    instructP.setSpacingBefore(6);
    if (isCompleted) {
      instructP.add(new Phrase("Statut du retrait en boutique :\n", instructTitleFont));
      instructP.add(
          new Phrase(
              "L'ensemble des articles commandés a été remis au client en boutique.",
              instructFont));
    } else {
      instructP.add(new Phrase("Consignes de retrait en boutique :\n", instructTitleFont));
      instructP.add(
          new Phrase(
              "Présentez ce bon ou votre QR code auprès de chaque commerçant partenaire concerné pour"
                  + " retirer vos articles.",
              instructFont));
    }
    return instructP;
  }

  private PdfPCell createRightQrCell(OrderResponse order) {
    PdfPCell rightCell = new PdfPCell();
    rightCell.setBackgroundColor(new Color(248, 250, 252));
    rightCell.setBorderColor(new Color(226, 232, 240));
    rightCell.setBorderWidth(1f);
    rightCell.setPadding(6f);
    rightCell.setHorizontalAlignment(Element.ALIGN_CENTER);

    boolean hasLoyaltyCode = order.clientCode() != null && !order.clientCode().isBlank();
    String codeToEncode = hasLoyaltyCode ? order.clientCode().trim() : order.orderReference();

    try {
      QRCodeWriter qrCodeWriter = new QRCodeWriter();
      BitMatrix bitMatrix = qrCodeWriter.encode(codeToEncode, BarcodeFormat.QR_CODE, 90, 90);
      BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
      Image qrImage = Image.getInstance(bufferedImage, null);
      qrImage.setAlignment(Element.ALIGN_CENTER);
      rightCell.addElement(qrImage);
    } catch (Exception ex) {
      log.warn(
          "Could not generate barcode QR code image for code {}: {}",
          codeToEncode,
          ex.getMessage());
    }

    Font monoFont = FontFactory.getFont(FontFactory.COURIER_BOLD, 11f, PRIMARY_COLOR);
    Paragraph codePara = new Paragraph(codeToEncode, monoFont);
    codePara.setAlignment(Element.ALIGN_CENTER);
    codePara.setSpacingBefore(4);
    rightCell.addElement(codePara);

    return rightCell;
  }

  private void addStoreSummariesAndItems(Document document, OrderResponse order)
      throws DocumentException {
    if (order.storeSummaries() == null || order.storeSummaries().isEmpty()) {
      return;
    }
    for (OrderStoreSummaryResponse storeSummary : order.storeSummaries()) {
      renderStoreSection(document, storeSummary);
    }
  }

  private void renderStoreSection(Document document, OrderStoreSummaryResponse storeSummary)
      throws DocumentException {
    Font storeTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, PRIMARY_COLOR);
    Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, PRIMARY_COLOR);
    Font itemFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);

    PdfPTable storeTable = new PdfPTable(4);
    storeTable.setWidthPercentage(100);
    storeTable.setWidths(new float[] {4.5f, 1.5f, 2f, 2f});
    storeTable.setSpacingAfter(10);
    storeTable.setHeaderRows(2);

    String safeStoreName =
        (storeSummary.storeName() != null && !storeSummary.storeName().isBlank())
            ? storeSummary.storeName()
            : "Commerce #" + storeSummary.storeId();
    String storeLabel = "COMMERCE PARTENAIRE : " + safeStoreName.toUpperCase(Locale.FRENCH);
    PdfPCell storeHeaderCell = new PdfPCell(new Phrase(storeLabel, storeTitleFont));
    storeHeaderCell.setColspan(4);
    storeHeaderCell.setBackgroundColor(STORE_HEADER_BG);
    storeHeaderCell.setPadding(6);
    storeTable.addCell(storeHeaderCell);

    addTableHeaderCell(storeTable, "Article", tableHeaderFont);
    addTableHeaderCell(storeTable, "Quantité", tableHeaderFont);
    addTableHeaderCell(storeTable, "Prix unitaire", tableHeaderFont);
    addTableHeaderCell(storeTable, "Sous-total", tableHeaderFont);

    if (storeSummary.items() != null) {
      for (OrderItemResponse item : storeSummary.items()) {
        addTableCell(storeTable, item.productName(), itemFont, Element.ALIGN_LEFT);
        addTableCell(storeTable, String.valueOf(item.quantity()), itemFont, Element.ALIGN_CENTER);
        addTableCell(storeTable, formatPrice(item.unitPrice()), itemFont, Element.ALIGN_RIGHT);
        addTableCell(storeTable, formatPrice(item.totalPrice()), itemFont, Element.ALIGN_RIGHT);
      }
    }

    PdfPCell subtotalLabel =
        new PdfPCell(new Phrase("Sous-total : " + safeStoreName, tableHeaderFont));
    subtotalLabel.setColspan(3);
    subtotalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
    subtotalLabel.setPadding(5);
    storeTable.addCell(subtotalLabel);

    PdfPCell subtotalVal =
        new PdfPCell(new Phrase(formatPrice(storeSummary.subtotal()), tableHeaderFont));
    subtotalVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
    subtotalVal.setPadding(5);
    storeTable.addCell(subtotalVal);

    document.add(storeTable);
  }

  private void addTotalsAndLoyalty(Document document, OrderResponse order)
      throws DocumentException {
    PdfPTable totalTable = new PdfPTable(2);
    totalTable.setWidthPercentage(100);
    totalTable.setWidths(new float[] {1f, 1f});
    totalTable.setSpacingAfter(15);

    Font bigTotalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, PRIMARY_COLOR);
    Font pointsFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ACCENT_COLOR);

    PdfPCell loyaltyCell = new PdfPCell();
    loyaltyCell.setBorder(PdfPCell.NO_BORDER);
    boolean hasClientCode = order.clientCode() != null && !order.clientCode().isBlank();
    if (hasClientCode) {
      // 1 euro = 1 point standard baseline reward
      long estimatedPoints = order.totalAmount() != null ? order.totalAmount().longValue() : 0L;
      loyaltyCell.addElement(
          new Paragraph("Points fidélité acquis : +" + estimatedPoints + " pts", pointsFont));
    } else {
      loyaltyCell.addElement(
          new Paragraph(
              "Points fidélité : aucun compte rattaché",
              new Font(FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, TEXT_MUTED))));
    }

    PdfPCell totalCell = new PdfPCell();
    totalCell.setBorder(PdfPCell.NO_BORDER);
    totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    Paragraph totalP =
        new Paragraph("TOTAL RÉGLÉ : " + formatPrice(order.totalAmount()), bigTotalFont);
    totalP.setAlignment(Element.ALIGN_RIGHT);
    totalCell.addElement(totalP);

    totalTable.addCell(loyaltyCell);
    totalTable.addCell(totalCell);
    document.add(totalTable);
  }

  private void addFooter(Document document, boolean isCompleted) throws DocumentException {
    Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 7, TEXT_MUTED);
    String purpose =
        isCompleted
            ? "de facture et justificatif d'achat."
            : "de bon de retrait en boutique et de confirmation d'achat.";
    Paragraph footer =
        new Paragraph(
            "Document officiel émis par ShopLoc Collectiv'IT — Plateforme de commerce de proximité"
                + " et fidélité.\nCe justificatif tient lieu "
                + purpose,
            footerFont);
    footer.setAlignment(Element.ALIGN_CENTER);
    document.add(footer);
  }

  private void addTableHeaderCell(PdfPTable table, String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setBackgroundColor(TABLE_HEADER_BG);
    cell.setPadding(5);
    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
    table.addCell(cell);
  }

  private void addTableCell(PdfPTable table, String text, Font font, int alignment) {
    PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
    cell.setPadding(4);
    cell.setHorizontalAlignment(alignment);
    table.addCell(cell);
  }

  private String formatPrice(BigDecimal price) {
    if (price == null) {
      return "0,00 €";
    }
    return String.format(Locale.FRENCH, "%.2f €", price);
  }

  private String formatStatusFrench(String status) {
    if (status == null) {
      return "INCONNU";
    }
    return switch (status.toUpperCase(Locale.ROOT)) {
      case "PAID" -> "PAYÉ";
      case "COMPLETED" -> "ARTICLES RÉCUPÉRÉS";
      case "PENDING_PAYMENT" -> "EN ATTENTE DE PAIEMENT";
      case "CANCELLED" -> "ANNULÉ";
      case "EXPIRED" -> "EXPIRÉ";
      default -> status.toUpperCase(Locale.ROOT);
    };
  }
}
