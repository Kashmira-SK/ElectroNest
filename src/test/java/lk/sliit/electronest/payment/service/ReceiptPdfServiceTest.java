package lk.sliit.electronest.payment.service;

import lk.sliit.electronest.common.web.StatusDisplay;
import lk.sliit.electronest.payment.model.*;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptPdfServiceTest {
    static Receipt receipt() {
        var payment = new Payment();
        payment.setTransactionId("TXN-12345678-1234-1234-1234-123456789012");
        payment.setTransactionReference("REF-12345678-1234-1234-1234-123456789012");
        payment.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        payment.setAmount(new BigDecimal("12650.00"));
        var receipt = new Receipt();
        receipt.setPayment(payment);
        receipt.setReceiptNumber("REC-12345678-1234-1234-1234-123456789012");
        receipt.setOrderNumber("ORD-12345678-1234-1234-1234-123456789012");
        receipt.setCustomerName("Anjali Perera");
        receipt.setCustomerEmail("anjali.perera@example.com");
        receipt.setDeliveryAddress("Apartment 12, Second Floor, 125 Long Garden Avenue,\nNugegoda, Western Province, 10250, Sri Lanka");
        receipt.setItemizedSummary("Mechanical keyboard x 1 - LKR 10000.00\nUSB-C charging cable x 2 - LKR 2000.00\nWireless mouse x 1 - LKR 1000.00");
        receipt.setSubtotal(new BigDecimal("13000.00"));
        receipt.setTaxAmount(new BigDecimal("150.00"));
        receipt.setShippingFee(new BigDecimal("500.00"));
        receipt.setDiscountAmount(new BigDecimal("1000.00"));
        receipt.setPromoCode("DEMO");
        receipt.setTotalAmount(new BigDecimal("12650.00"));
        receipt.setPaymentMethod("CREDIT_CARD");
        receipt.setIssuedAt(LocalDateTime.of(2026, 10, 6, 10, 30));
        return receipt;
    }

    @Test void longDetailsAndMultipleItemsFitOnePageWithoutChangingAmounts() throws Exception {
        var receipt = receipt();
        try (var reader = new PdfReader(new ReceiptPdfService(new StatusDisplay()).render(receipt))) {
            assertEquals(1, reader.getNumberOfPages());
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            for (String expected : new String[]{"ElectroNest", "Credit Card", "Anjali Perera", "Sri Lanka",
                    "Mechanical keyboard", "USB-C charging cable", "Wireless mouse", "13000.00", "150.00",
                    "500.00", "1000.00", "12650.00", "Amount paid", "DEMO"}) {
                assertTrue(text.contains(expected), expected);
            }
            assertTrue(text.replaceAll("\\s", "").contains(receipt.getPayment().getTransactionId()));
            assertTrue(text.replaceAll("\\s", "").contains(receipt.getReceiptNumber()));
            assertEquals(new BigDecimal("12650.00"), receipt.getTotalAmount());
        }
    }

    @Test void largeReceiptContinuesWithoutDroppingItems() throws Exception {
        var receipt = receipt();
        receipt.setItemizedSummary(java.util.stream.IntStream.rangeClosed(1, 60)
                .mapToObj(i -> "Product " + i + " x 1 - LKR 10.00")
                .collect(java.util.stream.Collectors.joining("\n")));
        try (var reader = new PdfReader(new ReceiptPdfService(new StatusDisplay()).render(receipt))) {
            assertTrue(reader.getNumberOfPages() > 1);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(new PdfTextExtractor(reader).getTextFromPage(page));
            }
            assertTrue(text.toString().contains("Product 60"));
            assertTrue(text.toString().contains("12650.00"));
        }
    }
}
