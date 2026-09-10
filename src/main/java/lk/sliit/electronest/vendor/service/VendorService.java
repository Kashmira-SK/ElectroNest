package lk.sliit.electronest.vendor.service;

import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.model.dto.VendorRegistrationRequest;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VendorService {

    private final VendorRepository vendorRepository;

    public VendorService(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    public Vendor registerVendor(Long userId, VendorRegistrationRequest request) {
        Vendor vendor = new Vendor();
        vendor.setUserId(userId);
        vendor.setBusinessName(request.getBusinessName());
        vendor.setRegistrationNumber(request.getRegistrationNumber());
        vendor.setBusinessAddress(request.getBusinessAddress());
        vendor.setContactPhone(request.getContactPhone());
        vendor.setStatus(VendorStatus.PENDING);
        return vendorRepository.save(vendor);
    }

    public List<Vendor> getVerificationQueue() {
        return vendorRepository.findByStatus(VendorStatus.PENDING);
    }

    public Vendor approveVendor(Long id) {
        Vendor vendor = getVendorOrThrow(id);
        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setRejectionReason(null);
        return vendorRepository.save(vendor);
    }

    public Vendor rejectVendor(Long id, String reason) {
        Vendor vendor = getVendorOrThrow(id);
        vendor.setStatus(VendorStatus.REJECTED);
        vendor.setRejectionReason(reason);
        return vendorRepository.save(vendor);
    }

    public Vendor requestMoreInfo(Long id, String message) {
        Vendor vendor = getVendorOrThrow(id);
        vendor.setStatus(VendorStatus.INFO_REQUESTED);
        vendor.setRejectionReason(message);
        return vendorRepository.save(vendor);
    }

    public Vendor suspendVendor(Long id) {
        Vendor vendor = getVendorOrThrow(id);
        vendor.setStatus(VendorStatus.SUSPENDED);
        return vendorRepository.save(vendor);
    }

    public Vendor reactivateVendor(Long id) {
        Vendor vendor = getVendorOrThrow(id);
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
}
