package lk.sliit.electronest.vendor.service;

/** Immutable values captured before the vendor transaction completes. */
public record VendorStatusEmail(Long vendorId, String recipient, String subject, String message) {
}
