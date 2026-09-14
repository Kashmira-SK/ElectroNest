package lk.sliit.electronest.vendor.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReasonRequest {

    @NotBlank(message = "A review reason is required")
    @Size(max = 500, message = "Review reason must not exceed 500 characters")
    private String reason;

    public ReasonRequest() {}

    public ReasonRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
