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
    private final lk.sliit.electronest.payment.service.SavedCardService savedCardService;

    public PaymentViewController(
            OrderService orderService,
            PaymentWorkflowService paymentService,
            CartService cartService,
            lk.sliit.electronest.payment.service.SavedCardService savedCardService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.cartService = cartService;
        this.savedCardService = savedCardService;
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
        if (order.getStatus().isTerminal()) return "redirect:/orders";

        model.addAttribute("order", order);
        model.addAttribute("savedCards", savedCardService.list(currentUser.getUser()));
        model.addAttribute(
                "paymentMethods",
                java.util.List.of(
                        PaymentMethod.CREDIT_CARD,
                        PaymentMethod.DEBIT_CARD,
                        PaymentMethod.CASH_ON_DELIVERY
                )
        );

        return "payment/payment";
    }

    @PostMapping("/payment")
    public String processPayment(
            @RequestParam Long orderId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) Long savedCardId,
            @RequestParam(defaultValue = "false") boolean saveCard,
            @RequestParam(required = false) String cardHolderName,
            @RequestParam(required = false) String cardNumber,
            @RequestParam(required = false) String expiryDate,
            @RequestParam(required = false) String cvv,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        PaymentRequest request = new PaymentRequest();
        request.setOrderId(orderId);
        request.setPaymentMethod(paymentMethod);
        request.setCardHolderName(cardHolderName);
        request.setCardNumber(cardNumber);
        request.setExpiryDate(expiryDate);
        request.setCvv(cvv);
        request.setSavedCardId(savedCardId);
        request.setSaveCard(saveCard);

        try {
            Payment payment = paymentService.processPayment(
                    request,
                    currentUser.getUser()
            );


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
