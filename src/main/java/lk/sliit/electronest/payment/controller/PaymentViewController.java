package lk.sliit.electronest.payment.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaymentViewController {

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/payment")
    public String payment() {
        return "payment/payment";
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/receipt")
    public String receipt() {
        return "payment/receipt";
    }
}
