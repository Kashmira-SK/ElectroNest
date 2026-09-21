package lk.sliit.electronest.vendor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class VendorEmailNotifier {
    private static final Logger log = LoggerFactory.getLogger(VendorEmailNotifier.class);
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public VendorEmailNotifier(ObjectProvider<JavaMailSender> mailSender,
                               @Value("${electronest.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(VendorStatusEmail event) {
        try {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null || from.isBlank()) {
                log.info("Vendor email skipped: SMTP/from is not configured (vendor={})", event.vendorId());
                return;
            }
            if (event.recipient() == null || event.recipient().isBlank()) {
                log.warn("Vendor email skipped: registered email is missing (vendor={})", event.vendorId());
                return;
            }
            SimpleMailMessage email = new SimpleMailMessage();
            email.setFrom(from);
            email.setTo(event.recipient());
            email.setSubject(event.subject());
            email.setText(event.message());
            sender.send(email);
        } catch (RuntimeException ex) {
            // The decision has already committed. Do not expose SMTP details or mail contents.
            log.warn("Vendor email failed (vendor={}, failure={}); decision remains saved",
                    event.vendorId(), ex.getClass().getSimpleName());
        }
    }
}
