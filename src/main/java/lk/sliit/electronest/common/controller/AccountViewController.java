package lk.sliit.electronest.common.controller;

import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.exception.VendorNotFoundException;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountViewController {

    private final UserRepository userRepository;
    private final VendorService vendorService;
    private final lk.sliit.electronest.payment.service.SavedCardService savedCardService;

    public AccountViewController(
            UserRepository userRepository,
            VendorService vendorService,
            lk.sliit.electronest.payment.service.SavedCardService savedCardService) {
        this.userRepository = userRepository;
        this.vendorService = vendorService;
        this.savedCardService = savedCardService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/settings")
    public String settings(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model) {

        User user = getUser(currentUser);
        Vendor vendor = null;

        try {
            vendor = vendorService.getVendorForUser(user.getId());
        } catch (VendorNotFoundException ignored) {
        }

        model.addAttribute("user", user);
        model.addAttribute("savedCards", savedCardService.list(user));
        model.addAttribute("vendor", vendor);
        model.addAttribute("hasSavedDelivery", hasSavedDelivery(user));

        return "account/settings";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/settings/cards")
    public String saveCard(@AuthenticationPrincipal CustomUserDetails currentUser,
                           @RequestParam lk.sliit.electronest.payment.model.PaymentMethod method,
                           @RequestParam String holder, @RequestParam String number,
                           @RequestParam String expiry, RedirectAttributes redirect) {
        try {
            savedCardService.save(getUser(currentUser), method, holder, number, expiry);
            redirect.addFlashAttribute("successMessage", "Card saved to your account.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/settings#payment";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/settings/cards/{id}/remove")
    public String removeCard(@AuthenticationPrincipal CustomUserDetails currentUser,
                             @org.springframework.web.bind.annotation.PathVariable Long id,
                             RedirectAttributes redirect) {
        try {
            savedCardService.remove(id, getUser(currentUser));
            redirect.addFlashAttribute("successMessage", "Card removed.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/settings#payment";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/settings/profile")
    public String updateProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String contactNumber,
            RedirectAttributes redirectAttributes) {

        User user = getUser(currentUser);

        try {
            String cleanName = requireText(fullName, "Full name");
            String cleanEmail = requireText(email, "Email").toLowerCase(java.util.Locale.ROOT);
            checkLength(cleanName, 100, "Full name");
            checkLength(cleanEmail, 150, "Email");

            if (!cleanEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new IllegalArgumentException("Enter a valid email address");
            }

            String cleanPhone = normalizePhone(contactNumber);

            user.setFullName(cleanName);
            user.setEmail(cleanEmail);
            user.setContactNumber(cleanPhone);

            userRepository.save(user);

            currentUser.getUser().setFullName(cleanName);
            currentUser.getUser().setEmail(cleanEmail);
            currentUser.getUser().setContactNumber(cleanPhone);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Profile updated."
            );
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("profileDraft", java.util.Map.of(
                    "fullName", fullName, "email", email, "contactNumber", contactNumber));
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "That email address is already being used."
            );
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("profileDraft", java.util.Map.of(
                    "fullName", fullName, "email", email, "contactNumber", contactNumber));
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return "redirect:/settings#profile";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/settings/address")
    public String updateAddress(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam String deliveryName,
            @RequestParam String deliveryPhone,
            @RequestParam String deliveryAddressLine1,
            @RequestParam(required = false) String deliveryAddressLine2,
            @RequestParam String deliveryCity,
            @RequestParam(required = false) String deliveryPostalCode,
            @RequestParam String deliveryCountry,
            RedirectAttributes redirectAttributes) {

        User user = getUser(currentUser);

        try {
            // Validate every field before changing the managed account.
            requireText(deliveryName, "Delivery name");
            normalizePhone(deliveryPhone);
            requireText(deliveryAddressLine1, "Address line 1");
            requireText(deliveryCity, "City");
            requireText(deliveryCountry, "Country");
            checkLength(deliveryName, 100, "Delivery name");
            checkLength(deliveryPhone, 20, "Phone");
            checkLength(deliveryAddressLine1, 200, "Address line 1");
            checkLength(deliveryAddressLine2, 200, "Address line 2");
            checkLength(deliveryCity, 100, "City");
            checkLength(deliveryPostalCode, 20, "Postal code");
            checkLength(deliveryCountry, 100, "Country");
            user.setDeliveryName(
                    requireText(deliveryName, "Delivery name")
            );
            user.setDeliveryPhone(
                    normalizePhone(deliveryPhone)
            );
            user.setDeliveryAddressLine1(
                    requireText(deliveryAddressLine1, "Address line 1")
            );
            user.setDeliveryAddressLine2(
                    optional(deliveryAddressLine2)
            );
            user.setDeliveryCity(
                    requireText(deliveryCity, "City")
            );
            user.setDeliveryPostalCode(
                    optional(deliveryPostalCode)
            );
            user.setDeliveryCountry(
                    requireText(deliveryCountry, "Country")
            );

            userRepository.save(user);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Delivery address saved."
            );
        } catch (IllegalArgumentException ex) {
            var draft = new java.util.HashMap<String, String>();
            draft.put("deliveryName", deliveryName);
            draft.put("deliveryPhone", deliveryPhone);
            draft.put("deliveryAddressLine1", deliveryAddressLine1);
            draft.put("deliveryAddressLine2", deliveryAddressLine2);
            draft.put("deliveryCity", deliveryCity);
            draft.put("deliveryPostalCode", deliveryPostalCode);
            draft.put("deliveryCountry", deliveryCountry);
            redirectAttributes.addFlashAttribute("addressDraft", draft);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return "redirect:/settings#delivery";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/settings/address/delete")
    public String deleteAddress(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        User user = getUser(currentUser);

        user.setDeliveryName(null);
        user.setDeliveryPhone(null);
        user.setDeliveryAddressLine1(null);
        user.setDeliveryAddressLine2(null);
        user.setDeliveryCity(null);
        user.setDeliveryPostalCode(null);
        user.setDeliveryCountry(null);

        userRepository.save(user);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Saved delivery address removed."
        );

        return "redirect:/settings#delivery";
    }

    private User getUser(CustomUserDetails currentUser) {
        return userRepository.findById(currentUser.getUser().getId())
                .orElseThrow(() ->
                        new IllegalStateException("User not found"));
    }

    private boolean hasSavedDelivery(User user) {
        return hasText(user.getDeliveryName())
                && hasText(user.getDeliveryPhone())
                && hasText(user.getDeliveryAddressLine1())
                && hasText(user.getDeliveryCity())
                && hasText(user.getDeliveryCountry());
    }

    private String normalizePhone(String value) {
        String phone = requireText(value, "Phone number")
                .replaceAll("[\\s()\\-]", "");

        if (!phone.matches("^\\+?\\d{7,15}$")) {
            throw new IllegalArgumentException(
                    "Enter a valid local or international phone number (7–15 digits)"
            );
        }

        return phone;
    }

    private void checkLength(String value, int maximum, String field) {
        if (value != null && value.trim().length() > maximum) {
            throw new IllegalArgumentException(field + " must be " + maximum + " characters or fewer");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }

        return value.trim();
    }

    private String optional(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
