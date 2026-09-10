package lk.sliit.electronest.vendor.service;

import lk.sliit.electronest.vendor.exception.DuplicateVendorApplicationException;
import lk.sliit.electronest.vendor.exception.InvalidVendorReviewReasonException;
import lk.sliit.electronest.vendor.exception.InvalidVendorStatusTransitionException;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.model.dto.VendorRegistrationRequest;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class VendorService {

    private final VendorRepository vendorRepository;

    public VendorService(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    @Transactional
    public Vendor registerVendor(Long userId, VendorRegistrationRequest request) {
        if (vendorRepository.existsByUserId(userId)) {
            throw new DuplicateVendorApplicationException(
                    "You already have a vendor application"
            );
        }

        String registrationNumber = request.getRegistrationNumber()
                .trim()
                .toUpperCase(Locale.ROOT);
        if (vendorRepository.existsByRegistrationNumberIgnoreCase(registrationNumber)) {
            throw new DuplicateVendorApplicationException(
                    "This business registration number is already in use"
            );
        }

        Vendor vendor = new Vendor();
        vendor.setUserId(userId);
        vendor.setBusinessName(request.getBusinessName().trim());
        vendor.setRegistrationNumber(registrationNumber);
        vendor.setBusinessAddress(request.getBusinessAddress().trim());
        vendor.setContactPhone(request.getContactPhone().trim());
        vendor.setStatus(VendorStatus.PENDING);
        return vendorRepository.save(vendor);
    }

    public List<Vendor> getVerificationQueue() {
        return vendorRepository.findByStatus(VendorStatus.PENDING);
    }

    public Vendor approveVendor(Long id) {
        Vendor vendor = getVendorOrThrow(id);
        requireStatus(vendor, VendorStatus.PENDING, "approve");
        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setRejectionReason(null);
        return vendorRepository.save(vendor);
    }

    public Vendor rejectVendor(Long id, String reason) {
        Vendor vendor = getVendorOrThrow(id);
        requireStatus(vendor, VendorStatus.PENDING, "reject");
        vendor.setStatus(VendorStatus.REJECTED);
        vendor.setRejectionReason(normalizeReviewReason(reason));
        return vendorRepository.save(vendor);
    }

    public Vendor requestMoreInfo(Long id, String message) {
        Vendor vendor = getVendorOrThrow(id);
        requireStatus(vendor, VendorStatus.PENDING, "request more information for");
        vendor.setStatus(VendorStatus.INFO_REQUESTED);
        vendor.setRejectionReason(normalizeReviewReason(message));
        return vendorRepository.save(vendor);
    }

    public Vendor suspendVendor(Long id) {
        Vendor vendor = getVendorOrThrow(id);
        requireStatus(vendor, VendorStatus.APPROVED, "suspend");
        vendor.setStatus(VendorStatus.SUSPENDED);
        return vendorRepository.save(vendor);
    }

    public Vendor reactivateVendor(Long id) {
        Vendor vendor = getVendorOrThrow(id);
        requireStatus(vendor, VendorStatus.SUSPENDED, "reactivate");
        vendor.setStatus(VendorStatus.APPROVED);
        return vendorRepository.save(vendor);
    }

    public void revokeVendor(Long id) {
        vendorRepository.deleteById(id);
    }

    public Vendor getVendorOrThrow(Long id) {
        return vendorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + id));
    }

    public Vendor getVendorForUser(Long userId) {
        return vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vendor application not found for the current user"
                ));
    }

    private void requireStatus(Vendor vendor, VendorStatus requiredStatus, String action) {
        if (vendor.getStatus() != requiredStatus) {
            throw new InvalidVendorStatusTransitionException(
                    "Cannot " + action + " vendor " + vendor.getId()
                            + " while status is " + vendor.getStatus()
                            + "; expected " + requiredStatus
            );
        }
    }

    private String normalizeReviewReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidVendorReviewReasonException("A review reason is required");
        }

        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 500) {
            throw new InvalidVendorReviewReasonException(
                    "Review reason must not exceed 500 characters"
            );
        }
        return normalizedReason;
    }
}
