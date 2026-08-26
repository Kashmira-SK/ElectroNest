package lk.sliit.electronest.vendor.controller;

import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/vendor")
public class VendorViewController {

    private final VendorService vendorService;

    public VendorViewController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("vendor", new Vendor());
        return "vendor/register";
    }

    @PostMapping("/register")
    public String submitRegistration(@ModelAttribute Vendor vendor, Model model) {
        Vendor saved = vendorService.registerVendor(vendor);
        model.addAttribute("vendor", saved);
        return "vendor/register-success";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/queue")
    public String showQueue(Model model) {
        model.addAttribute("vendors", vendorService.getVerificationQueue());
        return "vendor/queue";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/queue/{id}/approve")
    public String approve(@PathVariable Long id) {
        vendorService.approveVendor(id);
        return "redirect:/vendor/queue";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/queue/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam String reason) {
        vendorService.rejectVendor(id, reason);
        return "redirect:/vendor/queue";
    }
}
