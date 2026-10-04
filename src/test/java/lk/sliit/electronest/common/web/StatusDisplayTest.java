package lk.sliit.electronest.common.web;

import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.vendor.model.VendorStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusDisplayTest {
    private final StatusDisplay display = new StatusDisplay();

    @Test void formatsLabelsWithoutChangingEnumValues() {
        assertEquals("Info Requested", display.label(VendorStatus.INFO_REQUESTED));
        assertEquals("Pending Payment", display.label(lk.sliit.electronest.order.model.PaymentStatus.PENDING_PAYMENT));
        assertEquals("Credit Card", display.label("CREDIT_CARD"));
        assertEquals("INFO_REQUESTED", VendorStatus.INFO_REQUESTED.name());
        assertEquals("PENDING_PAYMENT", lk.sliit.electronest.order.model.PaymentStatus.PENDING_PAYMENT.name());
        assertEquals("Unknown", display.label(null));
        assertEquals("Unknown", display.label(" "));
    }

    @Test void mapsAccountAndApplicationStatesSeparately() {
        variants(AccountStatus.values(), "success", "neutral", "danger");
        variants(VendorStatus.values(), "warning", "success", "danger", "warning", "danger");
    }

    @Test void mapsFulfilmentAndBothPaymentDomains() {
        variants(OrderStatus.values(), "warning", "neutral", "success", "danger");
        variants(lk.sliit.electronest.order.model.PaymentStatus.values(), "warning", "success", "danger", "neutral");
        variants(lk.sliit.electronest.payment.model.PaymentStatus.values(), "warning", "success", "danger", "danger", "neutral");
    }

    @Test void unknownDomainsHaveNoAssumedWorkflowMeaning() {
        assertEquals("en-status-neutral", display.cssClass(Role.ADMIN));
        assertEquals("en-status-neutral", display.cssClass(null));
    }

    private void variants(Enum<?>[] values, String... variants) {
        assertEquals(values.length, variants.length);
        for (int i = 0; i < values.length; i++) {
            assertEquals("en-status-" + variants[i], display.cssClass(values[i]), values[i].name());
        }
    }
}
