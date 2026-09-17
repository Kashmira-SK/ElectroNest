package lk.sliit.electronest.common.web;

import lk.sliit.electronest.common.security.CustomUserDetails;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalViewModelAdvice {
    private final lk.sliit.electronest.vendor.repository.VendorRepository vendorRepository;

    public GlobalViewModelAdvice(lk.sliit.electronest.vendor.repository.VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    @ModelAttribute
    public void addAuthentication(Authentication authentication, Model model) {
        boolean loggedIn =
                authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        model.addAttribute("loggedIn", loggedIn);
        model.addAttribute("sellerAccess", false);
        model.addAttribute("accountSuspended", false);

        if (loggedIn && authentication.getPrincipal() instanceof CustomUserDetails details) {
            model.addAttribute("currentUser", details.getUser());
            boolean suspended = details.getUser().getStatus()
                    == lk.sliit.electronest.common.model.AccountStatus.SUSPENDED;
            model.addAttribute("accountSuspended", suspended);
            if (!suspended
                    && details.getUser().getRole() == lk.sliit.electronest.common.model.Role.VENDOR) {
                model.addAttribute("sellerAccess", vendorRepository.findByUser_Id(details.getUser().getId())
                        .filter(vendor -> vendor.getStatus() == lk.sliit.electronest.vendor.model.VendorStatus.APPROVED)
                        .isPresent());
            }
        }
    }
}
