package lk.sliit.electronest.payment.controller;

import lk.sliit.electronest.payment.model.Payment;
import lk.sliit.electronest.payment.model.PaymentRequest;
import lk.sliit.electronest.payment.model.PaymentStatus;
import lk.sliit.electronest.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Create / process a payment
    @PostMapping
    public ResponseEntity<Payment> processPayment(
            @RequestBody PaymentRequest request) {

        Payment savedPayment = paymentService.processPayment(request);

        return ResponseEntity.ok(savedPayment);
    }

    // Get payment by ID
    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPaymentById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                paymentService.getPaymentById(id)
        );
    }

    // Get payment by transaction ID
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<Payment> getPaymentByTransactionId(
            @PathVariable String transactionId) {

        return ResponseEntity.ok(
                paymentService.getPaymentByTransactionId(transactionId)
        );
    }

    // Get payments by order ID
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Payment>> getPaymentsByOrderId(
            @PathVariable Long orderId) {

        return ResponseEntity.ok(
                paymentService.getPaymentsByOrderId(orderId)
        );
    }

    // Get payments by customer ID
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Payment>> getPaymentsByCustomerId(
            @PathVariable Long customerId) {

        return ResponseEntity.ok(
                paymentService.getPaymentsByCustomerId(customerId)
        );
    }

    // Get all payments, optionally filtered by status
    @GetMapping
    public ResponseEntity<List<Payment>> getAllPayments(
            @RequestParam(required = false) PaymentStatus status) {

        return ResponseEntity.ok(
                paymentService.getAllPayments(status)
        );
    }

    // Update payment status
    @PutMapping("/{id}/status")
    public ResponseEntity<Payment> updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam PaymentStatus status) {

        return ResponseEntity.ok(
                paymentService.updatePaymentStatus(id, status)
        );
    }

    // Cancel payment
    @PutMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelPayment(
            @PathVariable Long id) {

        paymentService.cancelPayment(id);

        return ResponseEntity.ok().build();
    }

    // Refund payment
    @PutMapping("/{id}/refund")
    public ResponseEntity<Payment> processRefund(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                paymentService.processRefund(id)
        );
    }
}