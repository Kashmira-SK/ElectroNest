package lk.sliit.electronest;

import jakarta.servlet.Filter;
import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.payment.model.*;
import lk.sliit.electronest.payment.repository.PaymentRepository;
import lk.sliit.electronest.payment.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class ReceiptDownloadTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired PaymentRepository payments;
    @Autowired ReceiptRepository receipts;
    MockMvc mvc;
    User owner, other, admin, vendor;
    Receipt receipt;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        owner = user("owner", Role.CUSTOMER);
        other = user("other", Role.CUSTOMER);
        admin = user("admin", Role.ADMIN);
        vendor = user("vendor", Role.VENDOR);
        Payment payment = new Payment();
        payment.setCustomerId(owner.getId());
        payment.setOrderId(1L);
        payment.setOrderNumber("ORD-TEST");
        payment.setTransactionId("TXN-TEST");
        payment.setTransactionReference("REF-TEST");
        payment.setAmount(new BigDecimal("1250.00"));
        payment.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        payment.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        payment.setMaskedCardNumber("**** 1234");
        payments.saveAndFlush(payment);
        receipt = new Receipt();
        receipt.setPayment(payment);
        receipt.setReceiptNumber("REC-TEST");
        receipt.setOrderNumber("ORD-TEST");
        receipt.setCustomerName("Receipt Customer");
        receipt.setCustomerEmail(owner.getEmail());
        receipt.setDeliveryAddress("10 Test Road, Colombo");
        receipt.setItemizedSummary("Keyboard x 1 - LKR 1250.00");
        receipt.setSubtotal(payment.getAmount());
        receipt.setTotalAmount(payment.getAmount());
        receipt.setPaymentMethod("CREDIT_CARD");
        receipts.saveAndFlush(receipt);
    }

    @Test void ownerAndAdminDownloadRealPdf() throws Exception {
        for (User viewer : new User[]{owner, admin}) {
            byte[] bytes = mvc.perform(get(path()).session(session(viewer)))
                    .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                    .andExpect(header().string("Content-Disposition", "attachment; filename=\"ElectroNest-Receipt-REC-TEST.pdf\""))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn().getResponse().getContentAsByteArray();
            try (PdfReader reader = new PdfReader(bytes)) {
                assertTrue(reader.getNumberOfPages() >= 1);
                String text = new PdfTextExtractor(reader).getTextFromPage(1);
                for (String expected : new String[]{"ElectroNest", "REC-TEST", "TXN-TEST", "REF-TEST",
                        "ORD-TEST", "Receipt Customer", "Colombo", "Keyboard", "1250.00", "CREDIT_CARD", "Issued"}) {
                    assertTrue(text.contains(expected), expected);
                }
            }
        }
    }

    @Test void otherCustomerCannotSpoofOwner() throws Exception {
        mvc.perform(get(path()).param("customerId", owner.getId().toString()).session(session(other)))
                .andExpect(status().isForbidden());
    }

    @Test void vendorAndAnonymousCannotDownload() throws Exception {
        mvc.perform(get(path()).servletPath(path()).session(session(vendor))).andExpect(status().isForbidden());
        mvc.perform(get(path())).andExpect(status().is3xxRedirection());
    }

    @Test void unpaidReceiptCannotBeDownloadedAsProofOfPayment() throws Exception {
        receipt.getPayment().setPaymentStatus(PaymentStatus.PENDING);
        mvc.perform(get(path()).session(session(owner))).andExpect(status().isConflict());
    }

    @Test void missingReceiptReturnsNotFound() throws Exception {
        mvc.perform(get("/api/v1/receipts/999999999/download").session(session(owner)))
                .andExpect(status().isNotFound());
    }

    @Test void receiptPageOffersDownloadAndApplicationRendersPhoneRule() throws Exception {
        mvc.perform(get("/receipt").param("paymentId", receipt.getPayment().getId().toString())
                        .session(session(owner)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(path())));
        mvc.perform(get("/vendor/register").session(session(owner)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("(?:0[0-9]{9}|\\+94[0-9]{9})")));
    }

    private String path() { return "/api/v1/receipts/" + receipt.getId() + "/download"; }

    private User user(String name, Role role) {
        return users.saveAndFlush(User.builder().fullName(name).email(name + "@receipt.test")
                .password("unused").role(role).status(AccountStatus.ACTIVE).build());
    }

    private MockHttpSession session(User user) {
        var details = new CustomUserDetails(user);
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities())));
        return session;
    }
}
