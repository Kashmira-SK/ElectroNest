package lk.sliit.electronest.payment.controller;

import lk.sliit.electronest.payment.service.PaymentWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class PayHereNotificationController {
    private final PaymentWorkflowService payments;
    public PayHereNotificationController(PaymentWorkflowService payments) { this.payments = payments; }

    // Public and CSRF-exempt only here: the provider signature is checked before any mutation.
    @PostMapping(value = "/api/payments/payhere/notify", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> notifyPayment(@RequestParam MultiValueMap<String, String> fields) {
        if (fields.values().stream().anyMatch(values -> values.size() != 1))
            throw new IllegalArgumentException("Invalid payment notification");
        payments.receivePayHereNotification(fields.toSingleValueMap());
        return ResponseEntity.ok().build();
    }
}
