package lk.sliit.electronest.payment.service;

import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.payment.model.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Hosted sandbox checkout. The merchant secret never leaves the server. */
@Component
public class PayHereGateway {
    private final boolean enabled;
    private final String merchantId;
    private final String secret;
    private final String publicUrl;

    public PayHereGateway(@Value("${electronest.payhere.enabled:false}") boolean enabled,
                          @Value("${electronest.payhere.merchant-id:}") String merchantId,
                          @Value("${electronest.payhere.merchant-secret:}") String secret,
                          @Value("${electronest.payhere.public-url:}") String publicUrl) {
        this.enabled = enabled;
        this.merchantId = merchantId;
        this.secret = secret;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    public boolean isEnabled() { return enabled; }

    public void requireConfigured() {
        if (!enabled || merchantId.isBlank() || secret.isBlank())
            throw new IllegalStateException("Online payments are not configured. Please choose cash on delivery.");
        try {
            URI uri = URI.create(publicUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().isEmpty())
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Online payments are not configured. Please choose cash on delivery.");
        }
    }

    public Map<String, String> checkout(Payment payment, Order order) {
        requireConfigured();
        var fields = new LinkedHashMap<String, String>();
        fields.put("merchant_id", merchantId);
        fields.put("return_url", publicUrl + "/payment/payhere/status?paymentId=" + payment.getId());
        fields.put("cancel_url", fields.get("return_url"));
        fields.put("notify_url", publicUrl + "/api/payments/payhere/notify");
        fields.put("order_id", payment.getTransactionId());
        fields.put("items", payment.getOrderNumber());
        fields.put("currency", payment.getCurrency());
        fields.put("amount", payment.getAmount().setScale(2).toPlainString());
        String[] name = order.getDeliveryName().trim().split("\\s+", 2);
        fields.put("first_name", name[0]);
        fields.put("last_name", name.length > 1 ? name[1] : name[0]);
        fields.put("email", payment.getCustomerEmail());
        fields.put("phone", order.getDeliveryPhone());
        fields.put("address", order.getAddressLine1());
        fields.put("city", order.getCity());
        fields.put("country", order.getCountry());
        fields.put("hash", md5(merchantId + payment.getTransactionId() + fields.get("amount")
                + payment.getCurrency() + md5(secret)));
        return fields;
    }

    public void verify(Map<String, String> fields) {
        requireConfigured();
        for (String key : new String[]{"merchant_id", "order_id", "payhere_amount", "payhere_currency", "status_code", "md5sig"}) {
            if (fields.get(key) == null || fields.get(key).length() > 100)
                throw new IllegalArgumentException("Invalid payment notification");
        }
        String signature = fields.get("md5sig");
        String expected = md5(merchantId + fields.get("order_id") + fields.get("payhere_amount")
                + fields.get("payhere_currency") + fields.get("status_code") + md5(secret));
        if (!merchantId.equals(fields.get("merchant_id")) || !signature.matches("[A-Fa-f0-9]{32}")
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.toUpperCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
            throw new SecurityException("Invalid payment notification");
        if (!fields.get("payhere_amount").matches("[0-9]{1,10}\\.[0-9]{2}")
                || !java.util.Set.of("2", "0", "-1", "-2", "-3").contains(fields.get("status_code")))
            throw new IllegalArgumentException("Invalid payment notification");
    }

    // MD5 is required by PayHere's published checkout protocol, not a password hash.
    private static String md5(String text) {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
