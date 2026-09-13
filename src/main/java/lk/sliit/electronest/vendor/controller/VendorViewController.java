package lk.sliit.electronest.vendor.controller;

import jakarta.validation.Valid;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.exception.DuplicateVendorApplicationException;
import lk.sliit.electronest.vendor.exception.InvalidVendorReviewReasonException;
import lk.sliit.electronest.vendor.exception.InvalidVendorStatusTransitionException;
import lk.sliit.electronest.vendor.exception.VendorNotFoundException;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.dto.VendorRegistrationRequest;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/vendor")
public class VendorViewController {

    private final VendorService vendorService;

    public VendorViewController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @PreAuthorize("hasRole('VENDOR')")
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        if (!model.containsAttribute("registrationRequest")) {
            model.addAttribute("registrationRequest", new VendorRegistrationRequest());
        }
        return "vendor/register";
    }

    @PreAuthorize("hasRole('VENDOR')")
    @PostMapping("/register")
    public String submitRegistration(
            @Valid @ModelAttribute("registrationRequest") VendorRegistrationRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {
        if (bindingResult.hasErrors()) {
            return "vendor/register";
        }

        try {
            Vendor saved = vendorService.registerVendor(currentUser.getUser().getId(), request);
            model.addAttribute("vendor", saved);
            return "vendor/register-success";
        } catch (DuplicateVendorApplicationException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "vendor/register";
        } catch (DataIntegrityViolationException ex) {
            model.addAttribute(
                    "errorMessage",
                    "This vendor application conflicts with an existing record"
            );
            return "vendor/register";
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/queue")
    public String showQueue(Model model) {
        model.addAttribute("vendors", vendorService.getVerificationQueue());
        return "vendor/queue";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/queue/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return executeQueueAction(
                () -> vendorService.approveVendor(id),
                "Vendor approved successfully.",
                redirectAttributes
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/queue/{id}/reject")
    public String reject(
            @PathVariable Long id,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        return executeQueueAction(
                () -> vendorService.rejectVendor(id, reason),
                "Vendor application rejected.",
                redirectAttributes
        );
    }

    private String executeQueueAction(
            Runnable action,
            String successMessage,
            RedirectAttributes redirectAttributes) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (VendorNotFoundException
                 | InvalidVendorStatusTransitionException
                 | InvalidVendorReviewReasonException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/vendor/queue";
    }
}
