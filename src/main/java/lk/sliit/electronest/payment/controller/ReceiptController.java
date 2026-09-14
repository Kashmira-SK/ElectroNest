package lk.sliit.electronest.payment.controller;

import lk.sliit.electronest.payment.model.Receipt;
import lk.sliit.electronest.payment.service.ReceiptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    // Create receipt
    @PostMapping
    public ResponseEntity<Receipt> createReceipt(
            @RequestBody Receipt receipt) {

        Receipt savedReceipt = receiptService.createReceipt(receipt);

        return ResponseEntity.ok(savedReceipt);
    }

    // Get receipt by ID
    @GetMapping("/{id}")
    public ResponseEntity<Receipt> getReceiptById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                receiptService.getReceiptById(id)
        );
    }

    // Get receipt by receipt number
    @GetMapping("/number/{receiptNumber}")
    public ResponseEntity<Receipt> getReceiptByReceiptNumber(
            @PathVariable String receiptNumber) {

        return ResponseEntity.ok(
                receiptService.getReceiptByReceiptNumber(receiptNumber)
        );
    }

    // Get receipt by payment ID
    @GetMapping("/payment/{paymentId}")
    public ResponseEntity<Receipt> getReceiptByPaymentId(
            @PathVariable Long paymentId) {

        return ResponseEntity.ok(
                receiptService.getReceiptByPaymentId(paymentId)
        );
    }

    // Get receipt by transaction ID
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<Receipt> getReceiptByTransactionId(
            @PathVariable String transactionId) {

        return ResponseEntity.ok(
                receiptService.getReceiptByTransactionId(transactionId)
        );
    }

    // Get all receipts
    @GetMapping
    public ResponseEntity<List<Receipt>> getAllReceipts() {

        return ResponseEntity.ok(
                receiptService.getAllReceipts()
        );
    }
}