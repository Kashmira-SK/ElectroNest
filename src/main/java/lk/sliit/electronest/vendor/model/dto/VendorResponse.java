package lk.sliit.electronest.vendor.model.dto;

import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;

import java.time.LocalDateTime;

public class VendorResponse {

    private final Long id;
    private final String businessName;
    private final String registrationNumber;
    private final String businessAddress;
    private final String contactPhone;
    private final VendorStatus status;
    private final String reviewMessage;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public VendorResponse(
            Long id,
            String businessName,
            String registrationNumber,
            String businessAddress,
            String contactPhone,
            VendorStatus status,
            String reviewMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.businessName = businessName;
        this.registrationNumber = registrationNumber;
        this.businessAddress = businessAddress;
        this.contactPhone = contactPhone;
        this.status = status;
        this.reviewMessage = reviewMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static VendorResponse from(Vendor vendor) {
        return new VendorResponse(
                vendor.getId(),
                vendor.getBusinessName(),
                vendor.getRegistrationNumber(),
                vendor.getBusinessAddress(),
                vendor.getContactPhone(),
                vendor.getStatus(),
                vendor.getRejectionReason(),
                vendor.getCreatedAt(),
                vendor.getUpdatedAt()
        );
    }

    public Long getId() { return id; }
    public String getBusinessName() { return businessName; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getBusinessAddress() { return businessAddress; }
    public String getContactPhone() { return contactPhone; }
    public VendorStatus getStatus() { return status; }
    public String getReviewMessage() { return reviewMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
