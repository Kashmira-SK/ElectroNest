package lk.sliit.electronest.common.web;

import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.vendor.model.VendorStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Presentation only: never changes persisted values or workflow decisions. */
@Component("statusDisplay")
public class StatusDisplay {
    public String label(Object value) {
        if (value == null) return "Unknown";
        String name = value instanceof Enum<?> status ? status.name() : value.toString();
        if (name.isBlank()) return "Unknown";
        return Arrays.stream(name.toLowerCase(Locale.ROOT).trim().split("[_\\s]+"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    public String cssClass(Enum<?> value) {
        String variant = switch (value) {
            case AccountStatus status -> switch (status) {
                case ACTIVE -> "success";
                case SUSPENDED -> "danger";
                case DEACTIVATED -> "neutral";
            };
            case VendorStatus status -> switch (status) {
                case APPROVED -> "success";
                case PENDING, INFO_REQUESTED -> "warning";
                case REJECTED, SUSPENDED -> "danger";
            };
            case OrderStatus status -> switch (status) {
                case DELIVERED -> "success";
                case PENDING -> "warning";
                case CANCELLED -> "danger";
                case PROCESSING -> "neutral";
            };
            case lk.sliit.electronest.order.model.PaymentStatus status -> switch (status) {
                case PAID -> "success";
                case PENDING_PAYMENT -> "warning";
                case FAILED, CANCELLED -> "danger";
                case REFUNDED -> "neutral";
            };
            case lk.sliit.electronest.payment.model.PaymentStatus status -> switch (status) {
                case SUCCESSFUL -> "success";
                case PENDING -> "warning";
                case FAILED, CANCELLED -> "danger";
                case REFUNDED -> "neutral";
            };
            case null, default -> "neutral";
        };
        return "en-status-" + variant;
    }
}
