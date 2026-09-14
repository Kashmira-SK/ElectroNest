package lk.sliit.electronest.vendor.controller;

import jakarta.validation.Valid;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.dto.ReasonRequest;
import lk.sliit.electronest.vendor.model.dto.VendorRegistrationRequest;
import lk.sliit.electronest.vendor.model.dto.VendorResponse;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendors")
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    // Vendor self-registration
    @PreAuthorize("hasRole('VENDOR')")
    @PostMapping("/register")
    public ResponseEntity<VendorResponse> register(
            @Valid @RequestBody VendorRegistrationRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Vendor saved = vendorService.registerVendor(currentUser.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(VendorResponse.from(saved));
    }

    // Admin: view pending verification queue
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/queue")
    public ResponseEntity<List<VendorResponse>> getVerificationQueue() {
        List<VendorResponse> vendors = vendorService.getVerificationQueue()
                .stream()
                .map(VendorResponse::from)
                .toList();
        return ResponseEntity.ok(vendors);
    }

    // Vendor: view only the application owned by the authenticated account
    @PreAuthorize("hasRole('VENDOR')")
    @GetMapping("/me")
    public ResponseEntity<VendorResponse> getCurrentVendor(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Vendor vendor = vendorService.getVendorForUser(currentUser.getUser().getId());
        return ResponseEntity.ok(VendorResponse.from(vendor));
    }

    // Admin: get a single vendor by vendor-record id
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<VendorResponse> getVendor(@PathVariable Long id) {
        return ResponseEntity.ok(VendorResponse.from(vendorService.getVendorOrThrow(id)));
    }

    // Admin: approve a vendor
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/approve")
    public ResponseEntity<VendorResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(VendorResponse.from(vendorService.approveVendor(id)));
    }

    // Admin: reject a vendor, with reason
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/reject")
    public ResponseEntity<VendorResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody ReasonRequest body) {
        return ResponseEntity.ok(VendorResponse.from(vendorService.rejectVendor(id, body.getReason())));
    }

    // Admin: request more info from vendor
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/request-info")
    public ResponseEntity<VendorResponse> requestInfo(
            @PathVariable Long id,
            @Valid @RequestBody ReasonRequest body) {
        return ResponseEntity.ok(VendorResponse.from(vendorService.requestMoreInfo(id, body.getReason())));
    }

    // Admin: suspend an approved vendor
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/suspend")
    public ResponseEntity<VendorResponse> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(VendorResponse.from(vendorService.suspendVendor(id)));
    }

    // Admin: reactivate a suspended vendor
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/reactivate")
    public ResponseEntity<VendorResponse> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(VendorResponse.from(vendorService.reactivateVendor(id)));
    }

    // Admin: revoke (delete) a vendor account
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        vendorService.revokeVendor(id);
        return ResponseEntity.noContent().build();
    }
}
