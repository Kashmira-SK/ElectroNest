package lk.sliit.electronest.vendor.controller;

import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.dto.ReasonRequest;
import lk.sliit.electronest.vendor.service.VendorService;
import org.springframework.http.ResponseEntity;
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
    @PostMapping("/register")
    public ResponseEntity<Vendor> register(@RequestBody Vendor vendor) {
        Vendor saved = vendorService.registerVendor(vendor);
        return ResponseEntity.ok(saved);
    }

    // Admin: view pending verification queue
    @GetMapping("/queue")
    public ResponseEntity<List<Vendor>> getVerificationQueue() {
        return ResponseEntity.ok(vendorService.getVerificationQueue());
    }

    // Get a single vendor by id
    @GetMapping("/{id}")
    public ResponseEntity<Vendor> getVendor(@PathVariable Long id) {
        return ResponseEntity.ok(vendorService.getVendorOrThrow(id));
    }

    // Admin: approve a vendor
    @PutMapping("/{id}/approve")
    public ResponseEntity<Vendor> approve(@PathVariable Long id) {
        return ResponseEntity.ok(vendorService.approveVendor(id));
    }

    // Admin: reject a vendor, with reason
    @PutMapping("/{id}/reject")
    public ResponseEntity<Vendor> reject(@PathVariable Long id, @RequestBody ReasonRequest body) {
        return ResponseEntity.ok(vendorService.rejectVendor(id, body.getReason()));
    }

    // Admin: request more info from vendor
    @PutMapping("/{id}/request-info")
    public ResponseEntity<Vendor> requestInfo(@PathVariable Long id, @RequestBody ReasonRequest body) {
        return ResponseEntity.ok(vendorService.requestMoreInfo(id, body.getReason()));
    }

    // Admin: suspend an approved vendor
    @PutMapping("/{id}/suspend")
    public ResponseEntity<Vendor> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(vendorService.suspendVendor(id));
    }

    // Admin: reactivate a suspended vendor
    @PutMapping("/{id}/reactivate")
    public ResponseEntity<Vendor> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(vendorService.reactivateVendor(id));
    }

    // Admin: revoke (delete) a vendor account
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        vendorService.revokeVendor(id);
        return ResponseEntity.noContent().build();
    }
}
