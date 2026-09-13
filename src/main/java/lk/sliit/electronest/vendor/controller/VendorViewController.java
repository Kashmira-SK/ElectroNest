package lk.sliit.electronest.vendor.controller;

import jakarta.validation.Valid;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.exception.DuplicateVendorApplicationException;
import lk.sliit.electronest.vendor.exception.InvalidVendorReviewReasonException;
import lk.sliit.electronest.vendor.exception.InvalidVendorStatusTransitionException;
import lk.sliit.electronest.vendor.exception.VendorNotFoundException;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.dto.VendorProfileUpdateRequest;
import lk.sliit.electronest.vendor.model.dto.VendorRegistrationRequest;
import lk.sliit.electronest.vendor.service.VendorDocumentStorageService;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/vendor")
public class VendorViewController {

    private final VendorService vendorService;
    private final VendorDocumentStorageService documentStorageService;

    public VendorViewController(
            VendorService vendorService,
            VendorDocumentStorageService documentStorageService) {
        this.vendorService = vendorService;
        this.documentStorageService = documentStorageService;
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
            @RequestParam("document") MultipartFile document,
            Model model) {
        if (bindingResult.hasErrors()) {
            return "vendor/register";
        }

        String storedDocument = null;

        try {
            storedDocument = documentStorageService.store(document);

            Vendor saved = vendorService.registerVendor(
                    currentUser.getUser().getId(),
                    request,
                    storedDocument
            );

            model.addAttribute("vendor", saved);
            return "vendor/register-success";

        } catch (DuplicateVendorApplicationException ex) {
            documentStorageService.deleteQuietly(storedDocument);
            model.addAttribute("errorMessage", ex.getMessage());
            return "vendor/register";

        } catch (DataIntegrityViolationException ex) {
            documentStorageService.deleteQuietly(storedDocument);
            model.addAttribute(
                    "errorMessage",
                    "This vendor application conflicts with an existing record"
            );
            return "vendor/register";

        } catch (IllegalArgumentException | IllegalStateException ex) {
            documentStorageService.deleteQuietly(storedDocument);
            model.addAttribute("errorMessage", ex.getMessage());
            return "vendor/register";
        }
    }

    @PreAuthorize("hasRole('VENDOR')")
    @GetMapping("/status")
    public String showStatus(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {
        try {
            model.addAttribute(
                    "vendor",
                    vendorService.getVendorForUser(currentUser.getUser().getId())
            );
            return "vendor/status";
        } catch (VendorNotFoundException ex) {
            return "redirect:/vendor/register";
        }
    }

    @PreAuthorize("hasRole('VENDOR')")
    @GetMapping("/profile")
    public String showProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes) {
        Vendor vendor = vendorService.getVendorForUser(currentUser.getUser().getId());

        if (vendor.getStatus() != lk.sliit.electronest.vendor.model.VendorStatus.APPROVED
                && vendor.getStatus() != lk.sliit.electronest.vendor.model.VendorStatus.INFO_REQUESTED) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Your vendor details cannot be edited while the application is " + vendor.getStatus()
            );
            return "redirect:/vendor/status";
        }

        if (!model.containsAttribute("profileRequest")) {
            VendorProfileUpdateRequest request = new VendorProfileUpdateRequest();
            request.setBusinessName(vendor.getBusinessName());
            request.setBusinessAddress(vendor.getBusinessAddress());
            request.setContactPhone(vendor.getContactPhone());
            model.addAttribute("profileRequest", request);
        }

        model.addAttribute("vendor", vendor);
        return "vendor/profile";
    }

    @PreAuthorize("hasRole('VENDOR')")
    @PostMapping("/profile")
    public String updateProfile(
            @Valid @ModelAttribute("profileRequest") VendorProfileUpdateRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes) {

        Vendor vendor = vendorService.getVendorForUser(currentUser.getUser().getId());

        if (bindingResult.hasErrors()) {
            model.addAttribute("vendor", vendor);
            return "vendor/profile";
        }

        try {
            Vendor updated = vendorService.updateVendorDetails(
                    currentUser.getUser().getId(),
                    request
            );

            if (updated.getStatus()
                    == lk.sliit.electronest.vendor.model.VendorStatus.PENDING) {
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        "Your updated information has been submitted for review."
                );
                return "redirect:/vendor/status";
            }

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Vendor profile updated successfully."
            );
            return "redirect:/vendor/profile";
        } catch (VendorNotFoundException | InvalidVendorStatusTransitionException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/vendor/status";
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/queue/{id}/document")
    public ResponseEntity<Resource> viewDocument(@PathVariable Long id) {
        Vendor vendor = vendorService.getVendorOrThrow(id);

        if (vendor.getIdDocumentPath() == null) {
            return ResponseEntity.notFound().build();
        }

        String storedName = vendor.getIdDocumentPath();
        Resource resource = documentStorageService.load(storedName);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        String lowerName = storedName.toLowerCase();

        if (lowerName.endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        } else if (lowerName.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            mediaType = MediaType.IMAGE_JPEG;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=vendor-document" +
                                storedName.substring(storedName.lastIndexOf('.'))
                )
                .body(resource);
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
    @PostMapping("/queue/{id}/request-info")
    public String requestInfo(
            @PathVariable Long id,
            @RequestParam String message,
            RedirectAttributes redirectAttributes) {
        return executeQueueAction(
                () -> vendorService.requestMoreInfo(id, message),
                "More information requested from vendor.",
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
