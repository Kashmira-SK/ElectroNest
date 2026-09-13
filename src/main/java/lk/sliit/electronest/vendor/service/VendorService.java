package lk.sliit.electronest.vendor.service;

import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.vendor.exception.DuplicateVendorApplicationException;
import lk.sliit.electronest.vendor.exception.InvalidVendorReviewReasonException;
import lk.sliit.electronest.vendor.exception.InvalidVendorStatusTransitionException;
import lk.sliit.electronest.vendor.exception.VendorNotFoundException;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.model.dto.VendorProfileUpdateRequest;
import lk.sliit.electronest.vendor.model.dto.VendorRegistrationRequest;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class VendorService {

    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;

    public VendorService(
            VendorRepository vendorRepository,
            UserRepository userRepository) {
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Vendor registerVendor(Long userId, VendorRegistrationRequest request) {
        return registerVendor(userId, request, null);
    }

    @Transactional
    public Vendor registerVendor(
            Long userId,
            VendorRegistrationRequest request,
            String documentPath) {
        if (vendorRepository.existsByUser_Id(userId)) {
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Vendor vendor = new Vendor();
        vendor.setUser(user);
        vendor.setBusinessName(request.getBusinessName().trim());
        vendor.setRegistrationNumber(registrationNumber);
        vendor.setBusinessAddress(request.getBusinessAddress().trim());
        vendor.setContactPhone(request.getContactPhone().trim());
        vendor.setIdDocumentPath(documentPath);
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
        vendorRepository.delete(getVendorOrThrow(id));
    }

    public Vendor getVendorOrThrow(Long id) {
        return vendorRepository.findById(id)
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found: " + id));
    }

    public Vendor getVendorForUser(Long userId) {
        return vendorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new VendorNotFoundException(
                        "Vendor application not found for the current user"
                ));
    }

    @Transactional
    public Vendor updateVendorDetails(Long userId, VendorProfileUpdateRequest request) {
        Vendor vendor = getVendorForUser(userId);

        if (vendor.getStatus() != VendorStatus.APPROVED
                && vendor.getStatus() != VendorStatus.INFO_REQUESTED) {
            throw new InvalidVendorStatusTransitionException(
                    "Vendor details can only be edited after approval or when more information is requested"
            );
        }

        vendor.setBusinessName(request.getBusinessName().trim());
        vendor.setBusinessAddress(request.getBusinessAddress().trim());
        vendor.setContactPhone(request.getContactPhone().trim());

        if (vendor.getStatus() == VendorStatus.INFO_REQUESTED) {
            vendor.setStatus(VendorStatus.PENDING);
            vendor.setRejectionReason(null);
        }

        return vendorRepository.save(vendor);
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
