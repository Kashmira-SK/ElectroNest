package lk.sliit.electronest.payment.controller;

import lk.sliit.electronest.cart.service.CartService;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.service.OrderService;
import lk.sliit.electronest.payment.model.Payment;
import lk.sliit.electronest.payment.model.PaymentMethod;
import lk.sliit.electronest.payment.model.PaymentRequest;
import lk.sliit.electronest.payment.model.Receipt;
import lk.sliit.electronest.payment.service.PaymentWorkflowService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('CUSTOMER')")
public class PaymentViewController {

    private final OrderService orderService;
    private final PaymentWorkflowService paymentService;
    private final CartService cartService;

    public PaymentViewController(
            OrderService orderService,
            PaymentWorkflowService paymentService,
            CartService cartService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.cartService = cartService;
    }

    @GetMapping("/payment")
    public String payment(
            @RequestParam Long orderId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        Order order = orderService.getOrderByIdForViewer(
                orderId,
                currentUser.getUser()
        );

        Payment completedPayment = paymentService
                .findSuccessfulPaymentForOrderForViewer(
                        orderId,
                        currentUser.getUser()
                )
                .orElse(null);

        if (completedPayment != null) {
            return "redirect:/receipt?paymentId=" + completedPayment.getId();
        }

        model.addAttribute("order", order);
        model.addAttribute("paymentMethods", PaymentMethod.values());

        return "payment/payment";
    }

    @PostMapping("/payment")
    public String processPayment(
            @RequestParam Long orderId,
            @RequestParam PaymentMethod paymentMethod,
            @RequestParam(required = false) String cardHolderName,
            @RequestParam(required = false) String cardNumber,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        PaymentRequest request = new PaymentRequest();
        request.setOrderId(orderId);
        request.setPaymentMethod(paymentMethod);
        request.setCardHolderName(cardHolderName);
        request.setCardNumber(cardNumber);

        try {
            Payment payment = paymentService.processPayment(
                    request,
                    currentUser.getUser()
            );

            cartService.clearCart(currentUser.getUser().getId());

            return "redirect:/receipt?paymentId=" + payment.getId();

        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );

            return "redirect:/payment?orderId=" + orderId;
        }
    }

    @GetMapping("/receipt")
    public String receipt(
            @RequestParam Long paymentId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        Receipt receipt =
                paymentService.getReceiptByPaymentForViewer(
                        paymentId,
                        currentUser.getUser()
                );

        model.addAttribute("receipt", receipt);

        return "payment/receipt";
    }
}
