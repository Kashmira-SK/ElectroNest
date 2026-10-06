package lk.sliit.electronest.payment.service;

import lk.sliit.electronest.common.web.StatusDisplay;
import lk.sliit.electronest.payment.model.Receipt;
import org.openpdf.text.*;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** Presentation only: renders the existing receipt snapshot without recalculating the purchase. */
@Service
public class ReceiptPdfService {
    private static final Color INK = new Color(45, 53, 64);
    private static final Color MUTED = new Color(87, 101, 120);
    private static final Color BLUE = new Color(56, 107, 250);
    private static final Color SURFACE = new Color(239, 244, 255);
    private final StatusDisplay statusDisplay;

    public ReceiptPdfService(StatusDisplay statusDisplay) {
        this.statusDisplay = statusDisplay;
    }

    public byte[] render(Receipt receipt) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Document document = new Document(PageSize.A4, 42, 42, 36, 36)) {
            PdfWriter.getInstance(document, output);
            document.addTitle("ElectroNest Receipt " + receipt.getReceiptNumber());
            document.addAuthor("ElectroNest");
            document.open();
            document.add(new Paragraph("ElectroNest", font(24, true, BLUE)));
            Paragraph title = new Paragraph("Payment receipt", font(14, true, INK));
            title.setSpacingAfter(8);
            document.add(title);

            section(document, "Receipt & transaction");
            PdfPTable details = table();
            row(details, "Receipt number", receipt.getReceiptNumber());
            row(details, "Order number", receipt.getOrderNumber());
            row(details, "Transaction", receipt.getPayment().getTransactionId());
            row(details, "Reference", receipt.getPayment().getTransactionReference());
            row(details, "Issued", receipt.getIssuedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss")));
            document.add(details);

            section(document, "Customer & delivery");
            PdfPTable customer = table();
            row(customer, "Customer", receipt.getCustomerName());
            row(customer, "Email", receipt.getCustomerEmail());
            row(customer, "Delivery", receipt.getDeliveryAddress());
            document.add(customer);

            section(document, "Purchased items");
            PdfPTable items = new PdfPTable(1);
            items.setWidthPercentage(100);
            items.setSplitLate(false);
            if (receipt.getItemizedSummary() != null) {
                for (String item : receipt.getItemizedSummary().split("\\R")) {
                    if (item.isBlank()) continue;
                    PdfPCell cell = cell(item, false);
                    cell.setPadding(8);
                    cell.setBackgroundColor(new Color(247, 248, 250));
                    items.addCell(cell);
                }
            }
            document.add(items);

            section(document, "Order totals");
            PdfPTable totals = table();
            money(totals, "Subtotal", receipt.getSubtotal());
            money(totals, "Tax", receipt.getTaxAmount());
            money(totals, "Shipping", receipt.getShippingFee());
            row(totals, "Promo code", receipt.getPromoCode());
            money(totals, "Discount", receipt.getDiscountAmount());
            money(totals, "Total", receipt.getTotalAmount());
            document.add(totals);

            section(document, "Payment details");
            PdfPTable payment = table();
            row(payment, "Payment method", statusDisplay.label(receipt.getPaymentMethod()));
            document.add(payment);
            PdfPTable paid = table();
            paid.setSpacingBefore(6);
            // Downloads are restricted to successful payments by ReceiptController.
            PdfPCell label = new PdfPCell(new Phrase("Amount paid", font(13, true, INK)));
            PdfPCell amount = new PdfPCell(new Phrase(currency(receipt.getPayment().getAmount()), font(17, true, BLUE)));
            for (PdfPCell cell : new PdfPCell[]{label, amount}) {
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPadding(12);
                cell.setBackgroundColor(SURFACE);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            }
            amount.setHorizontalAlignment(Element.ALIGN_RIGHT);
            paid.addCell(label);
            paid.addCell(amount);
            document.add(paid);
        }
        return output.toByteArray();
    }

    private static Font font(float size, boolean bold, Color color) {
        return FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size, color);
    }

    private static void section(Document document, String text) {
        Paragraph heading = new Paragraph(text, font(11, true, INK));
        heading.setSpacingBefore(14);
        heading.setSpacingAfter(5);
        heading.setKeepTogether(true);
        document.add(heading);
    }

    private static PdfPTable table() {
        PdfPTable table = new PdfPTable(new float[]{1, 2.7f});
        table.setWidthPercentage(100);
        table.setSplitLate(false);
        return table;
    }

    private static PdfPCell cell(String text, boolean label) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(10, false, label ? MUTED : INK)));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(4);
        cell.setPaddingBottom(4);
        cell.setPaddingRight(10);
        cell.setLeading(0, 1.3f);
        return cell;
    }

    private static void row(PdfPTable table, String label, String value) {
        if (value == null || value.isBlank()) return;
        table.addCell(cell(label, true));
        table.addCell(cell(value, false));
    }

    private static String currency(BigDecimal value) {
        return value == null ? "" : "LKR " + value.toPlainString();
    }

    private static void money(PdfPTable table, String label, BigDecimal value) {
        if (value == null) return;
        table.addCell(cell(label, true));
        PdfPCell amount = cell(currency(value), false);
        amount.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(amount);
    }
}
