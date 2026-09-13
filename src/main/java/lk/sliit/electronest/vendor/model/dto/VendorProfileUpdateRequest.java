package lk.sliit.electronest.vendor.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VendorProfileUpdateRequest {

    @NotBlank(message = "Business name is required")
    @Size(max = 150, message = "Business name must not exceed 150 characters")
    private String businessName;

    @NotBlank(message = "Business address is required")
    @Size(max = 255, message = "Business address must not exceed 255 characters")
    private String businessAddress;

    @NotBlank(message = "Contact phone is required")
    @Pattern(
            regexp = "^[0-9+()\\-\\s]{7,20}$",
            message = "Enter a valid contact phone number"
    )
    private String contactPhone;

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getBusinessAddress() {
        return businessAddress;
    }

    public void setBusinessAddress(String businessAddress) {
        this.businessAddress = businessAddress;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }
}
