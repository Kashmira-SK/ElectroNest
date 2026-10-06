package lk.sliit.electronest.payment.controller;

import jakarta.servlet.Filter;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.model.OrderLineItem;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.payment.model.*;
import lk.sliit.electronest.payment.repository.PaymentRepository;
import lk.sliit.electronest.payment.repository.SavedCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class PaymentFormContextTest {
    static final String NUMBER = "4111111111111111";
    static final String FUTURE = YearMonth.now().plusYears(2).format(DateTimeFormatter.ofPattern("MM/yy"));
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired PaymentRepository payments;
    @Autowired SavedCardRepository cards;
    MockMvc mvc;
    MockHttpSession session;
    User customer;
    Order order;
    Product product;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        customer = users.saveAndFlush(User.builder().fullName("Card Customer").email("card-context@test.example")
                .password("unused").role(Role.CUSTOMER).status(AccountStatus.ACTIVE).build());
        var vendor = users.saveAndFlush(User.builder().fullName("Seller").email("card-seller@test.example")
                .password("unused").role(Role.VENDOR).status(AccountStatus.ACTIVE).build());
        product = new Product();
        product.setName("Keyboard"); product.setBrand("Brand"); product.setCategory("Accessories");
        product.setVendorId(vendor.getId()); product.setPrice(BigDecimal.TEN); product.setStockQuantity(5);
        products.saveAndFlush(product);
        order = new Order();
        order.setCustomer(customer); order.setDeliveryName("Card Customer"); order.setDeliveryPhone("0771234567");
        order.setAddressLine1("10 Main Street"); order.setCity("Colombo"); order.setCountry("Sri Lanka");
        var item = new OrderLineItem();
        item.setProductId(product.getId()); item.setVendor(vendor); item.setQuantity(1); item.setUnitPrice(BigDecimal.TEN);
        order.addLineItem(item); orders.saveAndFlush(order);
        var principal = new CustomUserDetails(customer);
        session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
    }

    @ParameterizedTest
    @CsvSource({
            "01/20,4111111111111111,975,expiryDate",
            "bad,4111111111111111,975,expiryDate",
            "13/99,4111111111111111,975,expiryDate",
            "FUTURE,4111111111111112,975,cardNumber",
            "FUTURE,4111111111111111,12,cvv",
            "FUTURE,4111111111111111,'',cvv"
    })
    void cardErrorsKeepSafeContextAndInlineFeedback(String expiry, String number, String cvv, String field) throws Exception {
        if (expiry.equals("FUTURE")) expiry = FUTURE;
        var result = submitCard(expiry, number, cvv);
        assertEquals("/payment?orderId=" + order.getId(), result.getResponse().getRedirectedUrl());
        assertEquals("CREDIT_CARD", result.getFlashMap().get("paymentChoice"));
        assertEquals("Card Customer", result.getFlashMap().get("paymentCardHolderName"));
        assertEquals(expiry, result.getFlashMap().get("paymentExpiryDate"));
        assertEquals(true, result.getFlashMap().get("paymentSaveCard"));
        assertEquals(field, result.getFlashMap().get("paymentErrorField"));
        assertFalse(result.getFlashMap().containsKey("cardNumber"));
        assertFalse(result.getFlashMap().containsKey("cvv"));
        assertFalse(result.getFlashMap().toString().contains(number));
        String html = renderError(result);
        assertTrue(Pattern.compile("value=\"CREDIT_CARD\"[^>]*checked").matcher(html).find());
        assertTrue(html.contains("value=\"Card Customer\""));
        assertTrue(html.contains("value=\"" + expiry + "\""));
        assertTrue(html.contains("id=\"" + field + "Error\""));
        assertFalse(Pattern.compile("<fieldset[^>]*id=\"cardFields\"[^>]*(?:hidden|disabled)").matcher(html).find());
        assertSensitiveInputsEmpty(html);
        assertTrue(payments.findByOrderId(order.getId()).isEmpty());
        assertEquals(5, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"CREDIT_CARD", "DEBIT_CARD", "CASH_ON_DELIVERY"})
    void normalCardAndCodPaymentsStillSucceed(PaymentMethod method) throws Exception {
        var request = request().param("paymentMethod", method.name());
        if (method != PaymentMethod.CASH_ON_DELIVERY) request.param("cardHolderName", "Card Customer")
                .param("cardNumber", NUMBER).param("expiryDate", FUTURE).param("cvv", "975");
        mvc.perform(request).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/receipt?paymentId=*"));
        var payment = payments.findByOrderId(order.getId()).getFirst();
        assertEquals(method == PaymentMethod.CASH_ON_DELIVERY ? PaymentStatus.PENDING : PaymentStatus.SUCCESSFUL,
                payment.getPaymentStatus());
        assertEquals(0, BigDecimal.TEN.compareTo(payment.getAmount()));
        assertEquals(4, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @Test void savedCardCvvFailureRetainsSelectionAndCanBeCorrected() throws Exception {
        var card = savedCard(FUTURE);
        var result = mvc.perform(request().param("savedCardId", card.getId().toString()).param("cvv", "12"))
                .andExpect(redirectedUrl("/payment?orderId=" + order.getId())).andReturn();
        assertEquals("saved:" + card.getId(), result.getFlashMap().get("paymentChoice"));
        assertEquals("cvv", result.getFlashMap().get("paymentErrorField"));
        String html = renderError(result);
        assertTrue(Pattern.compile("value=\"saved:" + card.getId() + "\"[^>]*checked").matcher(html).find());
        assertSensitiveInputsEmpty(html);
        mvc.perform(request().param("savedCardId", card.getId().toString()).param("cvv", "975"))
                .andExpect(redirectedUrlPattern("/receipt?paymentId=*"));
        assertEquals(PaymentStatus.SUCCESSFUL, payments.findByOrderId(order.getId()).getFirst().getPaymentStatus());
    }

    @Test void expiredSavedCardShowsErrorBesideSavedMethods() throws Exception {
        var card = savedCard("01/20");
        var result = mvc.perform(request().param("savedCardId", card.getId().toString()).param("cvv", "975"))
                .andExpect(redirectedUrl("/payment?orderId=" + order.getId())).andReturn();
        assertEquals("savedCard", result.getFlashMap().get("paymentErrorField"));
        assertTrue(renderError(result).contains("Enter a current or future expiry as MM/YY."));
        assertTrue(payments.findByOrderId(order.getId()).isEmpty());
    }

    SavedCard savedCard(String expiry) {
        var card = new SavedCard();
        card.setCustomerId(customer.getId()); card.setHolder("Card Customer"); card.setLast4("1111");
        card.setMethod(PaymentMethod.CREDIT_CARD); card.setExpiry(expiry); card.setFingerprint("test");
        return cards.saveAndFlush(card);
    }

    MvcResult submitCard(String expiry, String number, String cvv) throws Exception {
        return mvc.perform(request().param("paymentMethod", "CREDIT_CARD").param("cardHolderName", "Card Customer")
                .param("cardNumber", number).param("expiryDate", expiry).param("cvv", cvv).param("saveCard", "true"))
                .andReturn();
    }

    MockHttpServletRequestBuilder request() throws Exception {
        var page = mvc.perform(get("/payment").param("orderId", order.getId().toString()).session(session))
                .andExpect(status().isOk()).andReturn();
        var csrf = (CsrfToken) page.getRequest().getAttribute(CsrfToken.class.getName());
        return post("/payment").session(session).param("orderId", order.getId().toString())
                .param(csrf.getParameterName(), csrf.getToken());
    }

    String renderError(MvcResult result) throws Exception {
        return mvc.perform(get("/payment").param("orderId", order.getId().toString()).session(session)
                        .flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void assertSensitiveInputsEmpty(String html) {
        assertFalse(html.contains(NUMBER));
        assertFalse(Pattern.compile("id=\"(?:cardNumber|cvv)\"[^>]*value=\"[^\"]+\"").matcher(html).find());
    }
}
