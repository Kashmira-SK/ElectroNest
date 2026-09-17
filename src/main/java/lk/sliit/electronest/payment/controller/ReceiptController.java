package lk.sliit.electronest.payment.controller;

import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.payment.model.Receipt;
import lk.sliit.electronest.payment.service.PaymentWorkflowService;
import lk.sliit.electronest.payment.service.ReceiptPdfService;
import lk.sliit.electronest.payment.model.PaymentStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/receipts")
public class ReceiptController {

    private final PaymentWorkflowService paymentService;
    private final ReceiptPdfService receiptPdfService;

    public ReceiptController(PaymentWorkflowService paymentService, ReceiptPdfService receiptPdfService) {
        this.paymentService = paymentService;
        this.receiptPdfService = receiptPdfService;
    }

    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<byte[]> download(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Receipt receipt;
        try {
            receipt = paymentService.getReceiptForViewer(id, currentUser.getUser());
        } catch (SecurityException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this receipt");
        } catch (java.util.NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found");
        }
        if (receipt.getPayment().getPaymentStatus() != PaymentStatus.SUCCESSFUL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only successful payment receipts can be downloaded");
        }
        String number = receipt.getReceiptNumber().replaceAll("[^A-Za-z0-9_-]", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("ElectroNest-Receipt-" + number + ".pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(receiptPdfService.render(receipt));
    }

    @GetMapping("/my-receipts")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<Receipt>> myReceipts(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                paymentService.myReceipts(currentUser.getUser())
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<Receipt> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                paymentService.getReceiptForViewer(
                        id,
                        currentUser.getUser()
                )
        );
    }

    @GetMapping("/number/{receiptNumber}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<Receipt> getByNumber(
            @PathVariable String receiptNumber,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                paymentService.getReceiptByNumberForViewer(
                        receiptNumber,
                        currentUser.getUser()
                )
        );
    }

    @GetMapping("/payment/{paymentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<Receipt> getByPayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                paymentService.getReceiptByPaymentForViewer(
                        paymentId,
                        currentUser.getUser()
                )
        );
    }

    @GetMapping("/transaction/{transactionId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<Receipt> getByTransaction(
            @PathVariable String transactionId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                paymentService.getReceiptByTransactionForViewer(
                        transactionId,
                        currentUser.getUser()
                )
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Receipt>> getAll() {
        return ResponseEntity.ok(paymentService.allReceipts());
    }
}
