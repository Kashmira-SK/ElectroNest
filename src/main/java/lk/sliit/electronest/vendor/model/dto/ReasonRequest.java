package lk.sliit.electronest.vendor.model.dto;

public class ReasonRequest {

    private String reason;

    public ReasonRequest() {}

    public ReasonRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
