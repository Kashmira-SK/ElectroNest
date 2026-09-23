package lk.sliit.electronest.cart.promo;

import jakarta.servlet.Filter;
import lk.sliit.electronest.cart.model.CartItem;
import lk.sliit.electronest.cart.repository.CartItemRepository;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.payment.model.*;
import lk.sliit.electronest.payment.repository.ReceiptRepository;
import lk.sliit.electronest.payment.service.PaymentWorkflowService;
import lk.sliit.electronest.payment.service.ReceiptPdfService;
import lk.sliit.electronest.vendor.model.*;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.junit.jupiter.api.*;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class PromoCheckoutTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired VendorRepository vendors;
    @Autowired ProductRepository products;
    @Autowired CartItemRepository carts;
    @Autowired PromoCodeRepository codes;
    @Autowired OrderRepository orders;
    @Autowired PaymentWorkflowService payments;
    @Autowired ReceiptRepository receipts;
    @Autowired ReceiptPdfService pdf;
    @Autowired jakarta.persistence.EntityManager entityManager;
    MockMvc mvc;
    MockHttpSession session;
    String csrf;
    User customer;
    Product product;
    PromoCode promo;

    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        customer = user(Role.CUSTOMER);
        customer.setDeliveryName("Customer");
        customer.setDeliveryPhone("0771234567");
        customer.setDeliveryAddressLine1("42 Galle Road");
        customer.setDeliveryCity("Colombo");
        customer.setDeliveryCountry("Sri Lanka");
        users.saveAndFlush(customer);
        var vendor = new Vendor();
        vendor.setUser(user(Role.VENDOR));
        vendor.setBusinessName("Promo Store");
        vendor.setRegistrationNumber(UUID.randomUUID().toString());
        vendor.setStatus(VendorStatus.APPROVED);
        vendors.saveAndFlush(vendor);
        product = new Product();
        product.setName("Promo product");
        product.setBrand("Brand");
        product.setCategory("Audio");
        product.setPrice(new BigDecimal("1000.00"));
        product.setStockQuantity(10);
        product.setVendorId(vendor.getId());
        products.saveAndFlush(product);
        carts.saveAndFlush(new CartItem(customer.getId(), product.getId(), 2, BigDecimal.ONE));
        promo = new PromoCode();
        promo.setCode("welcome10");
        promo.setDiscountType(PromoCode.DiscountType.PERCENTAGE);
        promo.setDiscountValue(BigDecimal.TEN);
        codes.saveAndFlush(promo);
        var principal = new CustomUserDetails(customer);
        session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        var page = mvc.perform(get("/checkout").session(session)).andExpect(status().isOk()).andReturn();
        csrf = ((CsrfToken) page.getRequest().getAttribute(CsrfToken.class.getName())).getToken();
    }

    @Test void applyTwiceRemoveAndInvalidCodeNeverStackDiscounts() throws Exception {
        for (int i = 0; i < 2; i++) apply();
        mvc.perform(get("/checkout").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("WELCOME10 applied.")))
                .andExpect(content().string(containsString("1,800.00")));
        mvc.perform(post("/checkout/promo").session(session).param("_csrf", csrf)
                        .param("action", "remove").accept("application/json"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quote.discount").value(0))
                .andExpect(jsonPath("$.quote.total").value(2000));
        apply();
        mvc.perform(post("/checkout/promo").session(session).param("_csrf", csrf)
                        .param("code", "UNKNOWN").accept("application/json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.quote.total").value(2000))
                .andExpect(jsonPath("$.error", containsString("continue at full price")));
        mvc.perform(post("/checkout/order").session(session).param("_csrf", csrf)).andExpect(status().is3xxRedirection());
        assertEquals(new BigDecimal("2000.00"), orders.findByCustomer_Id(customer.getId()).getFirst().totalAmount());
    }

    @Test void trustedSnapshotFeedsOrderPaymentReceiptAndHistoryDespiteForgedAmounts() throws Exception {
        apply();
        // A live price change must be reflected at order creation, not the earlier quote or cart snapshot.
        product.setPrice(new BigDecimal("1500.00"));
        products.saveAndFlush(product);
        mvc.perform(post("/checkout/order").session(session).param("_csrf", csrf)
                        .param("discountAmount", "999999").param("total", "0.01").param("promoCode", "FORGED"))
                .andExpect(status().is3xxRedirection());
        var order = orders.findByCustomer_Id(customer.getId()).getFirst();
        assertEquals("WELCOME10", order.getPromoCode());
        assertEquals(new BigDecimal("300.00"), order.getDiscountAmount());
        assertEquals(new BigDecimal("2700.00"), order.totalAmount());
        assertNull(session.getAttribute("checkoutPromo:" + customer.getId()));
        promo.setDiscountValue(new BigDecimal("90"));
        promo.setActive(false);
        codes.saveAndFlush(promo);
        mvc.perform(get("/payment").param("orderId", order.getId().toString()).session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("WELCOME10")))
                .andExpect(content().string(containsString("2,700.00")));
        PaymentRequest request = new PaymentRequest();
        request.setOrderId(order.getId());
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        request.setCardHolderName("Customer");
        request.setCardNumber("4111111111111111");
        request.setExpiryDate("12/99");
        request.setCvv("123");
        request.setAmount(new BigDecimal("0.01"));
        var payment = payments.processPayment(request, customer);
        assertEquals(new BigDecimal("2700.00"), payment.getAmount());
        var receipt = receipts.findByPaymentId(payment.getId()).orElseThrow();
        assertEquals(new BigDecimal("3000.00"), receipt.getSubtotal());
        assertEquals(new BigDecimal("300.00"), receipt.getDiscountAmount());
        assertEquals(new BigDecimal("2700.00"), receipt.getTotalAmount());
        assertEquals("WELCOME10", receipt.getPromoCode());
        entityManager.flush();
        entityManager.clear();
        assertEquals(new BigDecimal("2700.00"), orders.findById(order.getId()).orElseThrow().totalAmount());
        mvc.perform(get("/receipt").param("paymentId", payment.getId().toString()).session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("WELCOME10")))
                .andExpect(content().string(containsString("300.00")));
        mvc.perform(get("/orders").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("2,700.00")));
        try (PdfReader reader = new PdfReader(pdf.render(receipts.findById(receipt.getId()).orElseThrow()))) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(text.contains("WELCOME10"));
            assertTrue(text.contains("Discount: LKR 300.00"));
            assertTrue(text.contains("Total: LKR 2700.00"));
        }
    }

    @Test void expiredAfterApplyRequiresReviewOfFullPriceBeforeOrdering() throws Exception {
        apply();
        promo.setValidUntil(Instant.now().minusSeconds(1));
        codes.saveAndFlush(promo);
        mvc.perform(post("/checkout/order").session(session).param("_csrf", csrf))
                .andExpect(redirectedUrl("/checkout")).andExpect(flash().attribute("promoError", containsString("expired")));
        assertTrue(orders.findByCustomer_Id(customer.getId()).isEmpty());
        assertNull(session.getAttribute("checkoutPromo:" + customer.getId()));
    }

    @Test void normalFormWorksWithoutJavascriptAndCsrfIsRequired() throws Exception {
        mvc.perform(post("/checkout/promo").session(session).param("code", "WELCOME10").servletPath("/checkout/promo"))
                .andExpect(redirectedUrl("/access-denied"));
        assertNull(session.getAttribute("checkoutPromo:" + customer.getId()));
        mvc.perform(post("/checkout/promo").session(session).param("_csrf", csrf).param("code", "welcome10"))
                .andExpect(redirectedUrl("/checkout"));
        mvc.perform(get("/checkout").session(session)).andExpect(content().string(containsString("WELCOME10 applied.")));
    }

    @Test void apiOrderRejectsForgedDiscountAndNormalizesCode() throws Exception {
        String payload = """
                {"deliveryName":"Customer", "deliveryPhone":"0771234567",
                 "addressLine1":"42 Galle Road", "city":"Colombo", "country":"Sri Lanka",
                 "promoCode":" welcome10 ", "discountAmount":999999, "totalAmount":0.01,
                 "items":[{"productId":%d,"quantity":2,"unitPrice":0.01}]}
                """.formatted(product.getId());
        mvc.perform(post("/api/orders").servletPath("/api/orders").session(session)
                        .header("X-CSRF-TOKEN", csrf).contentType("application/json").content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(1800))
                .andExpect(jsonPath("$.discountAmount").value(200)).andExpect(jsonPath("$.promoCode").value("WELCOME10"));
    }

    private void apply() throws Exception {
        mvc.perform(post("/checkout/promo").session(session).param("_csrf", csrf)
                        .param("code", " welcome10 ").param("discount", "999999").accept("application/json"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quote.code").value("WELCOME10"))
                .andExpect(jsonPath("$.quote.subtotal").value(2000))
                .andExpect(jsonPath("$.quote.discount").value(200)).andExpect(jsonPath("$.quote.total").value(1800));
    }

    private User user(Role role) {
        return users.saveAndFlush(User.builder().fullName("Promo " + role).email(UUID.randomUUID() + "@example.com")
                .password("unused").role(role).status(AccountStatus.ACTIVE).build());
    }
}
