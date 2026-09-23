package lk.sliit.electronest.payment.service;

import lk.sliit.electronest.payment.model.Receipt;
import org.openpdf.text.Document;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** Presentation only: renders the existing receipt snapshot without recalculating the purchase. */
@Service
public class ReceiptPdfService {
    public byte[] render(Receipt receipt) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Document document = new Document()) {
            PdfWriter.getInstance(document, output);
            document.addTitle("ElectroNest Receipt " + receipt.getReceiptNumber());
            document.addAuthor("ElectroNest");
            document.open();
            document.add(new Paragraph("ElectroNest", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22)));
            document.add(new Paragraph("Payment receipt", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15)));
            line(document, "Receipt number", receipt.getReceiptNumber());
            line(document, "Transaction", receipt.getPayment().getTransactionId());
            line(document, "Reference", receipt.getPayment().getTransactionReference());
            line(document, "Order number", receipt.getOrderNumber());
            line(document, "Customer", receipt.getCustomerName());
            line(document, "Email", receipt.getCustomerEmail());
            line(document, "Delivery", receipt.getDeliveryAddress());
            line(document, "Purchased items", receipt.getItemizedSummary());
            money(document, "Subtotal", receipt.getSubtotal());
            money(document, "Tax", receipt.getTaxAmount());
            money(document, "Shipping", receipt.getShippingFee());
            line(document, "Promo code", receipt.getPromoCode());
            money(document, "Discount", receipt.getDiscountAmount());
            money(document, "Total", receipt.getTotalAmount());
            line(document, "Payment method", receipt.getPaymentMethod());
            money(document, "Amount paid", receipt.getPayment().getAmount());
            line(document, "Issued", receipt.getIssuedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss")));
        }
        return output.toByteArray();
    }

    private void money(Document document, String label, BigDecimal value) {
        if (value != null) line(document, label, "LKR " + value.toPlainString());
    }

    private void line(Document document, String label, String value) {
        if (value == null || value.isBlank()) return;
        Paragraph paragraph = new Paragraph(label + ": " + value, FontFactory.getFont(FontFactory.HELVETICA, 11));
        paragraph.setSpacingBefore(8);
        document.add(paragraph);
    }
}
