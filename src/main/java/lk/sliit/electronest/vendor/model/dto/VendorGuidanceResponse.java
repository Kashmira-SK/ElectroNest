package lk.sliit.electronest.vendor.model.dto;

import lk.sliit.electronest.vendor.model.VendorStatus;

public record VendorGuidanceResponse(
        VendorStatus status,
        String title,
        String message,
        String nextAction
) {
}
