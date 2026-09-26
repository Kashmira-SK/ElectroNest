package lk.sliit.electronest.payment.service;

import jakarta.servlet.Filter;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.controller.dto.*;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.service.OrderService;
import lk.sliit.electronest.payment.model.*;
import lk.sliit.electronest.payment.repository.*;
import lk.sliit.electronest.vendor.model.*;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.junit.jupiter.api.*;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false",
        "electronest.payhere.enabled=true", "electronest.payhere.merchant-id=1234567",
        "electronest.payhere.merchant-secret=test-only-secret", "electronest.payhere.public-url=https://shop.example.test"})
@ActiveProfiles("test")
@Transactional
class PayHereIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired VendorRepository vendors;
    @Autowired ProductRepository products;
    @Autowired OrderService orders;
    @Autowired PaymentWorkflowService workflow;
    @Autowired PaymentRepository payments;
    @Autowired ReceiptRepository receipts;
    MockMvc mvc;
    User customer, seller;
    Product product;
    Order order;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        customer=user(Role.CUSTOMER); seller=user(Role.VENDOR);
        Vendor vendor=new Vendor(); vendor.setUser(seller); vendor.setBusinessName("Store");
        vendor.setRegistrationNumber(UUID.randomUUID().toString()); vendor.setStatus(VendorStatus.APPROVED); vendors.saveAndFlush(vendor);
        product=new Product(); product.setName("Gateway product"); product.setBrand("Brand"); product.setCategory("Audio");
        product.setPrice(new BigDecimal("100.00")); product.setStockQuantity(5); product.setVendorId(vendor.getId()); products.saveAndFlush(product);
        order=orders.createOrder(new CreateOrderRequest("Buyer Test", "0771234567", "42 Road", null, "Colombo", null, "Sri Lanka",
                List.of(new OrderLineItemRequest(product.getId(),2))),customer);
        order.setDiscountAmount(new BigDecimal("20.00")); order.setPromoCode("TEST10");
    }

    @Test void checkoutUsesDiscountedServerTotalAndReservesOnce() {
        var fields=workflow.beginPayHere(order.getId(),customer);
        assertEquals("180.00", fields.get("amount"));
        assertEquals(md5("1234567"+fields.get("order_id")+"180.00LKR"+md5("test-only-secret")),fields.get("hash"));
        assertFalse(fields.containsValue("test-only-secret"));
        assertEquals(3,product.getStockQuantity());
        assertThrows(IllegalStateException.class,()->workflow.beginPayHere(order.getId(),customer));
        assertEquals(1,payments.findByOrderId(order.getId()).size());
        assertTrue(receipts.findByPaymentId(payment().getId()).isEmpty());
        assertFalse(workflow.readyForFulfilment(order));
    }

    @Test void signedSuccessCreatesOneInvoiceAndReturnCannotConfirmPayment() throws Exception {
        workflow.beginPayHere(order.getId(),customer);
        Payment p=payment();
        mvc.perform(get("/payment/payhere/status").param("paymentId",p.getId().toString()).param("status_code","2").session(session(customer)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Waiting for PayHere confirmation")));
        assertEquals(PaymentStatus.PENDING,p.getPaymentStatus());
        notifyThroughHttp(notification("2","180.00","LKR")).andExpect(status().isOk());
        notifyThroughHttp(notification("2","180.00","LKR")).andExpect(status().isOk());
        workflow.receivePayHereNotification(notification("0","180.00","LKR"));
        workflow.receivePayHereNotification(notification("-2","180.00","LKR"));
        assertEquals(PaymentStatus.SUCCESSFUL,p.getPaymentStatus());
        assertEquals(3,product.getStockQuantity());
        Receipt receipt=receipts.findByPaymentId(p.getId()).orElseThrow();
        assertEquals(new BigDecimal("180.00"),receipt.getTotalAmount());
        assertEquals("TEST10",receipt.getPromoCode());
        assertEquals("PAYHERE",receipt.getPaymentMethod());
        mvc.perform(get("/api/v1/receipts/"+receipt.getId()+"/download").session(session(customer)))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/api/v1/receipts/"+receipt.getId()+"/download").session(session(user(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
        assertTrue(workflow.readyForFulfilment(order));
        mvc.perform(get("/receipt").param("paymentId",p.getId().toString()).session(session(customer)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("TEST10")));
    }

    @Test void invalidSignaturesAndSignedMismatchedAmountsCannotPay() throws Exception {
        workflow.beginPayHere(order.getId(),customer);
        var forged=notification("2","180.00","LKR"); forged.put("md5sig","0".repeat(32));
        notifyThroughHttp(forged).andExpect(status().isForbidden());
        notifyThroughHttp(notification("2","1.00","LKR")).andExpect(status().isForbidden());
        notifyThroughHttp(notification("2","180.00","USD")).andExpect(status().isForbidden());
        var wrongMerchant=notification("2","180.00","LKR"); wrongMerchant.put("merchant_id","7654321");
        notifyThroughHttp(wrongMerchant).andExpect(status().isForbidden());
        assertEquals(PaymentStatus.PENDING,payment().getPaymentStatus());
        assertTrue(receipts.findByPaymentId(payment().getId()).isEmpty());
    }

    @Test void signedFailureReleasesStockOnlyOnceAndLateSuccessRequiresReview() {
        workflow.beginPayHere(order.getId(),customer);
        workflow.receivePayHereNotification(notification("-2","180.00","LKR"));
        workflow.receivePayHereNotification(notification("-2","180.00","LKR"));
        assertEquals(5,product.getStockQuantity());
        workflow.receivePayHereNotification(notification("2","180.00","LKR"));
        assertTrue(payment().isGatewayReviewRequired());
        assertFalse(workflow.readyForFulfilment(order));
        assertTrue(receipts.findByPaymentId(payment().getId()).isEmpty());
        assertThrows(IllegalStateException.class,()->workflow.beginPayHere(order.getId(),customer));
    }

    @Test void pendingAndSuccessfulGatewayPaymentsCannotBeLocallyCancelledOrRefunded() {
        workflow.beginPayHere(order.getId(),customer);
        assertThrows(IllegalStateException.class,()->workflow.cancel(payment().getId(),customer));
        assertThrows(IllegalStateException.class,()->workflow.cancelOrderPayment(order));
        workflow.receivePayHereNotification(notification("2","180.00","LKR"));
        assertThrows(IllegalStateException.class,()->workflow.refund(payment().getId()));
        assertEquals(PaymentStatus.SUCCESSFUL,payment().getPaymentStatus());
        assertEquals(3,product.getStockQuantity());
    }

    @Test void chargebackStopsFulfilmentWithoutInventingARefundOrRestockingDeliveredGoods() {
        workflow.beginPayHere(order.getId(),customer);
        workflow.receivePayHereNotification(notification("2","180.00","LKR"));
        workflow.receivePayHereNotification(notification("-3","180.00","LKR"));
        assertFalse(workflow.readyForFulfilment(order));
        assertTrue(payment().isGatewayReviewRequired());
        assertEquals(PaymentStatus.FAILED,payment().getPaymentStatus());
        assertEquals(3,product.getStockQuantity());
    }

    @Test void hostedUiRequiresCsrfAndOwnershipAndDoesNotCollectCards() throws Exception {
        var session=session(customer);
        var page=mvc.perform(get("/payment").param("orderId",order.getId().toString()).session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("PayHere sandbox")))
                .andExpect(content().string(not(containsString("name=\"cardNumber\"")))).andReturn();
        String csrf=((CsrfToken)page.getRequest().getAttribute(CsrfToken.class.getName())).getToken();
        mvc.perform(post("/payment/payhere").param("orderId",order.getId().toString()).session(session))
                .andExpect(status().is3xxRedirection());
        assertTrue(payments.findByOrderId(order.getId()).isEmpty());
        String html=mvc.perform(post("/payment/payhere").param("orderId",order.getId().toString()).header("X-CSRF-TOKEN",csrf).session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("https://sandbox.payhere.lk/pay/checkout")))
                .andExpect(content().string(not(containsString("test-only-secret")))).andReturn().getResponse().getContentAsString();
        String externalForm=html.substring(html.indexOf("<form action=\"https://sandbox.payhere.lk"));
        externalForm=externalForm.substring(0,externalForm.indexOf("</form>"));
        assertFalse(externalForm.contains("_csrf"));
        User other=user(Role.CUSTOMER);
        assertThrows(SecurityException.class,()->workflow.beginPayHere(order.getId(),other));
        mvc.perform(get("/payment/payhere/status").param("paymentId",payment().getId().toString()).session(session(other)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/payhere/notify").servletPath("/api/payments/payhere/notify").contentType("application/x-www-form-urlencoded"))
                .andExpect(status().isBadRequest());
    }

    @Test void directCardApiCannotBypassHostedCheckoutButCodStillWorks() {
        PaymentRequest request=new PaymentRequest(); request.setOrderId(order.getId()); request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        assertThrows(IllegalArgumentException.class,()->workflow.processPayment(request,customer));
        request.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        Payment p=workflow.processPayment(request,customer);
        assertEquals(PaymentStatus.PENDING,p.getPaymentStatus()); assertFalse(p.isPayHere());
    }

    @Test void incompleteConfigurationFailsClosed() {
        var gateway=new PayHereGateway(true,"","","https://shop.example.test");
        assertThrows(IllegalStateException.class,gateway::requireConfigured);
        assertThrows(IllegalStateException.class,()->new PayHereGateway(true,"123","secret","http://localhost").requireConfigured());
    }

    private org.springframework.test.web.servlet.ResultActions notifyThroughHttp(Map<String,String> fields) throws Exception {
        var request=post("/api/payments/payhere/notify").servletPath("/api/payments/payhere/notify").contentType("application/x-www-form-urlencoded");
        fields.forEach(request::param); return mvc.perform(request);
    }
    private Map<String,String> notification(String status,String amount,String currency) {
        var fields=new HashMap<String,String>(); fields.put("merchant_id","1234567"); fields.put("order_id",payment().getTransactionId());
        fields.put("payhere_amount",amount); fields.put("payhere_currency",currency); fields.put("status_code",status);
        fields.put("md5sig",md5("1234567"+payment().getTransactionId()+amount+currency+status+md5("test-only-secret"))); return fields;
    }
    private static String md5(String input) {
        try { return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }
    private Payment payment() { return payments.findByOrderId(order.getId()).getFirst(); }
    private User user(Role role) { return users.saveAndFlush(User.builder().fullName("Gateway "+role).email(UUID.randomUUID()+"@example.com")
            .password("unused").role(role).status(AccountStatus.ACTIVE).build()); }
    private MockHttpSession session(User user) {
        var details=new CustomUserDetails(user); var session=new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(new UsernamePasswordAuthenticationToken(details,null,details.getAuthorities()))); return session;
    }
}
