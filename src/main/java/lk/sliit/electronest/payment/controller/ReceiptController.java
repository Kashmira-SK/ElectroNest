package lk.sliit.electronest.payment.controller;

import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.payment.model.Receipt;
import lk.sliit.electronest.payment.service.PaymentWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/receipts")
public class ReceiptController {

    private final PaymentWorkflowService paymentService;

    public ReceiptController(PaymentWorkflowService paymentService) {
        this.paymentService = paymentService;
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
