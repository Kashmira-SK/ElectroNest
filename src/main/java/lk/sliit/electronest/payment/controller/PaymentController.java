package lk.sliit.electronest.payment.controller;

import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.payment.model.Payment;
import lk.sliit.electronest.payment.model.PaymentRequest;
import lk.sliit.electronest.payment.model.PaymentStatus;
import lk.sliit.electronest.payment.service.PaymentWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentWorkflowService paymentService;

    public PaymentController(PaymentWorkflowService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Payment> processPayment(
            @RequestBody PaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                paymentService.processPayment(request, currentUser.getUser())
        );
    }

    @GetMapping("/my-payments")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<Payment>> myPayments(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                paymentService.myPayments(currentUser.getUser())
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<Payment> getPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                paymentService.getPaymentForViewer(id, currentUser.getUser())
        );
    }

    @GetMapping("/transaction/{transactionId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<Payment> getByTransaction(
            @PathVariable String transactionId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                paymentService.getByTransactionForViewer(
                        transactionId,
                        currentUser.getUser()
                )
        );
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<List<Payment>> getByOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                paymentService.getOrderPaymentsForViewer(
                        orderId,
                        currentUser.getUser()
                )
        );
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Payment>> getByCustomer(
            @PathVariable Long customerId) {

        return ResponseEntity.ok(
                paymentService.getCustomerPayments(customerId)
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Payment>> getAll(
            @RequestParam(required = false) PaymentStatus status) {

        return ResponseEntity.ok(
                paymentService.getAllPayments(status)
        );
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Payment> updateStatus(
            @PathVariable Long id,
            @RequestParam PaymentStatus status) {

        return ResponseEntity.ok(
                paymentService.updateStatus(id, status)
        );
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<Payment> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(
                paymentService.cancel(id, currentUser.getUser())
        );
    }

    @PutMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Payment> refund(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.refund(id));
    }
}
