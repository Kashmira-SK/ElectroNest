package lk.sliit.electronest.vendor.service;

import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.vendor.model.*;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"electronest.mail.from=seller-support@example.com", "logging.level.root=WARN"})
@ActiveProfiles("test")
class VendorEmailNotificationTest {
    @Autowired VendorService service;
    @Autowired VendorRepository vendors;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JavaMailSender mail;

    @Test void approvalSendsToRegisteredEmailOnlyAfterCommit() {
        Vendor vendor = applicant();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.approveVendor(vendor.getId());
            verifyNoInteractions(mail);
        });
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertArrayEquals(new String[]{vendor.getUser().getEmail()}, message.getValue().getTo());
        assertTrue(message.getValue().getText().contains("approved"));
        assertTrue(message.getValue().getText().contains("manage your store"));
        assertEquals(VendorStatus.APPROVED, vendors.findById(vendor.getId()).orElseThrow().getStatus());
    }

    @Test void failedMailLeavesDecisionAndSellerRolePersisted() {
        Vendor vendor = applicant();
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(() -> service.approveVendor(vendor.getId()));
        assertEquals(VendorStatus.APPROVED, vendors.findById(vendor.getId()).orElseThrow().getStatus());
        assertEquals(Role.VENDOR, users.findById(vendor.getUser().getId()).orElseThrow().getRole());
        verify(mail).send(any(SimpleMailMessage.class));
    }

    @Test void rolledBackDecisionDoesNotSendEmail() {
        Vendor vendor = applicant();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.rejectVendor(vendor.getId(), "Invalid document");
            status.setRollbackOnly();
        });
        verifyNoInteractions(mail);
        assertEquals(VendorStatus.PENDING, vendors.findById(vendor.getId()).orElseThrow().getStatus());
    }

    @Test void rejectionAndInformationRequestIncludeAdminInstructions() {
        Vendor rejected = applicant();
        Vendor needsInfo = applicant();
        service.rejectVendor(rejected.getId(), " Registration does not match ");
        service.requestMoreInfo(needsInfo.getId(), " Upload a clearer document ");
        verify(mail).send(argThat((SimpleMailMessage email) -> email.getText().endsWith("Registration does not match")));
        verify(mail).send(argThat((SimpleMailMessage email) -> email.getText().endsWith("Upload a clearer document")));
    }

    @Test void noSmtpConfigurationIsSafe() {
        @SuppressWarnings("unchecked")
        var provider = (org.springframework.beans.factory.ObjectProvider<JavaMailSender>)
                mock(org.springframework.beans.factory.ObjectProvider.class);
        var notifier = new VendorEmailNotifier(provider, "");
        assertDoesNotThrow(() -> notifier.send(new VendorStatusEmail(1L, "seller@example.com", "Decision", "Saved")));
        verifyNoInteractions(mail);
    }

    private Vendor applicant() {
        String key = UUID.randomUUID().toString();
        User user = new User();
        user.setFullName("Applicant");
        user.setEmail(key + "@example.com");
        user.setPassword("unused");
        user.setRole(Role.CUSTOMER);
        users.save(user);
        Vendor vendor = new Vendor();
        vendor.setUser(user);
        vendor.setBusinessName("Applicant Store");
        vendor.setRegistrationNumber(key);
        vendor.setIdDocumentPath("document.pdf");
        return vendors.save(vendor);
    }
}
