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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service generating official ShopLoc proof of purchase and click & collect receipt in PDF format.
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
  "PMD.CognitiveComplexity"
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
   * Generates a byte array containing the official PDF receipt for an order.
   *
   * @param order the completed order details
   * @return byte array representing the rendered PDF document
   */
  public byte[] generateReceiptPdf(OrderResponse order) {
    if (order == null) {
      throw new IllegalArgumentException("Order cannot be null");
    }

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4, 36, 36, 40, 40);
      PdfWriter.getInstance(document, out);
      document.open();

      addHeader(document);
      addOrderMetadata(document, order);
      addStoreSummariesAndItems(document, order);
      addTotalsAndLoyalty(document, order);
      addClickAndCollectInstructionsWithQrCode(document, order);
      addFooter(document);

      document.close();
      return out.toByteArray();
    } catch (DocumentException ex) {
      log.error(
          "Failed to generate receipt PDF for order {}: {}",
          order.orderReference(),
          ex.getMessage(),
          ex);
      throw new IllegalStateException(
          "Erreur lors de la génération du document PDF de preuve d'achat", ex);
    } catch (Exception ex) {
      log.error(
          "Unexpected error during PDF generation for order {}: {}",
          order.orderReference(),
          ex.getMessage(),
          ex);
      throw new IllegalStateException("Impossible de générer le reçu PDF", ex);
    }
  }

  private void addHeader(Document document) throws DocumentException {
    Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, PRIMARY_COLOR);
    Paragraph brand = new Paragraph("ShopLoc", brandFont);
    brand.setAlignment(Element.ALIGN_CENTER);
    document.add(brand);

    Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, ACCENT_COLOR);
    Paragraph title = new Paragraph("PREUVE D'ACHAT & BON DE RETRAIT CLICK & COLLECT", titleFont);
    title.setAlignment(Element.ALIGN_CENTER);
    title.setSpacingAfter(15);
    document.add(title);
  }

  private void addOrderMetadata(Document document, OrderResponse order) throws DocumentException {
    PdfPTable metaTable = new PdfPTable(2);
    metaTable.setWidthPercentage(100);
    metaTable.setWidths(new float[] {1f, 1f});
    metaTable.setSpacingAfter(15);

    Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, PRIMARY_COLOR);
    Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);

    PdfPCell leftCell = new PdfPCell();
    leftCell.setBorder(PdfPCell.NO_BORDER);
    leftCell.addElement(new Paragraph("Référence : " + order.orderReference(), labelFont));
    String dateStr = order.createdAt() != null ? DATE_FORMATTER.format(order.createdAt()) : "N/A";
    leftCell.addElement(new Paragraph("Date d'achat : " + dateStr, valueFont));
    leftCell.addElement(new Paragraph("Statut : " + order.status(), labelFont));

    PdfPCell rightCell = new PdfPCell();
    rightCell.setBorder(PdfPCell.NO_BORDER);
    rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    if (order.customerEmail() != null && !order.customerEmail().isBlank()) {
      rightCell.addElement(new Paragraph("Email client : " + order.customerEmail(), valueFont));
    }
    String clientCode = order.clientCode() != null ? order.clientCode() : "N/A";
    rightCell.addElement(new Paragraph("Code client / Carte : " + clientCode, labelFont));

    metaTable.addCell(leftCell);
    metaTable.addCell(rightCell);
    document.add(metaTable);
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
        new PdfPCell(new Phrase("Sous-total " + safeStoreName, tableHeaderFont));
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

  private void addClickAndCollectInstructionsWithQrCode(Document document, OrderResponse order)
      throws DocumentException {
    PdfPTable boxTable = new PdfPTable(1);
    boxTable.setWidthPercentage(100);
    boxTable.setSpacingAfter(15);

    PdfPCell boxCell = new PdfPCell();
    boxCell.setBackgroundColor(new Color(248, 250, 252));
    boxCell.setBorderColor(new Color(203, 213, 225));
    boxCell.setPadding(10);

    Font boxTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, PRIMARY_COLOR);
    Font boxTextFont = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);
    Font monoFont = FontFactory.getFont(FontFactory.COURIER_BOLD, 11, PRIMARY_COLOR);

    Paragraph boxTitle = new Paragraph("CONSIGNES DE RETRAIT EN BOUTIQUE", boxTitleFont);
    boxTitle.setAlignment(Element.ALIGN_CENTER);
    boxTitle.setSpacingAfter(4);
    boxCell.addElement(boxTitle);

    Paragraph boxText =
        new Paragraph(
            "Pour retirer votre commande Click & Collect, présentez ce document ou votre application mobile "
                + "auprès de chaque commerçant partenaire concerné. Le commerçant scannera votre QR Code ou saisira votre code fidélité.",
            boxTextFont);
    boxText.setAlignment(Element.ALIGN_CENTER);
    boxText.setSpacingAfter(8);
    boxCell.addElement(boxText);

    // QR Code Generation
    boolean hasLoyaltyCode = order.clientCode() != null && !order.clientCode().isBlank();
    String codeToEncode = hasLoyaltyCode ? order.clientCode().trim() : order.orderReference();

    try {
      QRCodeWriter qrCodeWriter = new QRCodeWriter();
      BitMatrix bitMatrix = qrCodeWriter.encode(codeToEncode, BarcodeFormat.QR_CODE, 140, 140);
      BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
      Image qrImage = Image.getInstance(bufferedImage, null);
      qrImage.setAlignment(Element.ALIGN_CENTER);
      boxCell.addElement(qrImage);
    } catch (Exception ex) {
      log.warn(
          "Could not generate barcode QR code image for code {}: {}",
          codeToEncode,
          ex.getMessage());
    }

    String labelText =
        hasLoyaltyCode
            ? "Code fidélité client : " + codeToEncode
            : "Référence commande : " + codeToEncode;
    Paragraph codePara = new Paragraph(labelText, monoFont);
    codePara.setAlignment(Element.ALIGN_CENTER);
    codePara.setSpacingBefore(4);
    boxCell.addElement(codePara);

    boxTable.addCell(boxCell);
    document.add(boxTable);
  }

  private void addFooter(Document document) throws DocumentException {
    Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 7, TEXT_MUTED);
    Paragraph footer =
        new Paragraph(
            "Document officiel émis par ShopLoc Collectiv'IT — Plateforme de commerce de proximité et fidélité.\n"
                + "Ce justificatif tient lieu de preuve de paiement et de bon de retrait en boutique.",
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
}
