package lk.sliit.electronest.order.service;

import jakarta.servlet.Filter;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.controller.dto.*;
import lk.sliit.electronest.order.model.Order;
import lk.sliit.electronest.order.model.OrderStatus;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.payment.model.*;
import lk.sliit.electronest.payment.service.PaymentWorkflowService;
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
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class VendorFulfilmentTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired VendorRepository vendors;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderService service;
    @Autowired PaymentWorkflowService payments;
    @Autowired jakarta.persistence.EntityManager em;
    MockMvc mvc;
    User customer, sellerA, sellerB, admin;
    Order order;
    Payment payment;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        customer=user(Role.CUSTOMER); sellerA=user(Role.VENDOR); sellerB=user(Role.VENDOR); admin=user(Role.ADMIN);
        Product a = product(sellerA), b = product(sellerB);
        order=service.createOrder(new CreateOrderRequest("Buyer", "0771234567", "42 Road", null, "Colombo", null, "Sri Lanka",
                List.of(new OrderLineItemRequest(a.getId(),1),new OrderLineItemRequest(b.getId(),1))),customer);
        PaymentRequest request=new PaymentRequest(); request.setOrderId(order.getId()); request.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        payment=payments.processPayment(request,customer);
    }

    @Test void sellerChangesOnlyOwnedLinesAndCodWaitsForAllDeliveries() {
        service.updateFulfilmentStatus(order.getId(),sellerA,OrderStatus.PROCESSING,false);
        service.updateFulfilmentStatus(order.getId(),sellerA,OrderStatus.DELIVERED,false);
        assertEquals(OrderStatus.DELIVERED,order.statusForVendor(sellerA.getId()));
        assertEquals(OrderStatus.PENDING,order.statusForVendor(sellerB.getId()));
        assertEquals(OrderStatus.PROCESSING,order.getStatus());
        assertEquals(PaymentStatus.PENDING,payment.getPaymentStatus());
        em.flush(); em.clear();
        Order saved=orders.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING,saved.statusForVendor(sellerB.getId()));
        service.updateFulfilmentStatus(order.getId(),sellerB,OrderStatus.PROCESSING,false);
        service.updateFulfilmentStatus(order.getId(),sellerB,OrderStatus.DELIVERED,false);
        assertEquals(OrderStatus.DELIVERED,saved.getStatus());
        assertEquals(PaymentStatus.SUCCESSFUL,payments.getPaymentForViewer(payment.getId(),customer).getPaymentStatus());
        assertDoesNotThrow(() -> service.updateFulfilmentStatus(order.getId(),sellerA,OrderStatus.DELIVERED,false));
    }

    @Test void vendorCannotCancelMixedOrderOrSpoofAdminOverride() {
        assertThrows(IllegalStateException.class,()->service.updateFulfilmentStatus(order.getId(),sellerA,OrderStatus.CANCELLED,true));
        assertEquals(OrderStatus.PENDING,order.getStatus());
        assertEquals(PaymentStatus.PENDING,payment.getPaymentStatus());
    }

    @Test void unrelatedSellerCannotChangeOrReadTheOrder() {
        User other=user(Role.VENDOR); product(other);
        assertThrows(SecurityException.class,()->service.updateFulfilmentStatus(order.getId(),other,OrderStatus.PROCESSING,false));
        assertThrows(SecurityException.class,()->service.getOrderByIdForViewer(order.getId(),other));
    }

    @Test void customerCancellationCancelsAllUndeliveredItemsAndReleasesStock() {
        service.requestCancellation(order.getId(),customer);
        assertEquals(OrderStatus.CANCELLED,order.getStatus());
        order.getLineItems().forEach(item -> {
            assertEquals(OrderStatus.CANCELLED,item.effectiveStatus());
            assertEquals(5, products.findById(item.getProductId()).orElseThrow().getStockQuantity());
        });
        assertEquals(PaymentStatus.CANCELLED,payment.getPaymentStatus());
    }

    @Test void partialDeliveryCannotTriggerWholeOrderRefundOrStockRestoration() {
        service.updateFulfilmentStatus(order.getId(),sellerA,OrderStatus.PROCESSING,false);
        service.updateFulfilmentStatus(order.getId(),sellerA,OrderStatus.DELIVERED,false);
        assertThrows(IllegalStateException.class,()->payments.cancelOrderPayment(order));
        assertThrows(IllegalStateException.class,()->service.requestCancellation(order.getId(),customer));
        assertEquals(PaymentStatus.PENDING,payment.getPaymentStatus());
    }

    @Test void legacyStatusesAndDiscountedCodSharesStayConsistent() {
        order.getLineItems().forEach(item->item.setFulfilmentStatus(null));
        order.setDiscountAmount(new BigDecimal("0.01"));
        assertEquals(order.totalAmount(),order.amountForVendor(sellerA.getId()).add(order.amountForVendor(sellerB.getId())));
        service.updateFulfilmentStatus(order.getId(),sellerA,OrderStatus.PROCESSING,false);
        assertEquals(OrderStatus.PENDING,order.statusForVendor(sellerB.getId()));
    }

    @Test void apiAndSellerPageShowOnlyOwnedStatusAndCustomerSeesBoth() throws Exception {
        var session=session(sellerA);
        var page=mvc.perform(get("/vendor/orders").session(session)).andExpect(status().isOk()).andReturn();
        var csrf=((CsrfToken)page.getRequest().getAttribute(CsrfToken.class.getName())).getToken();
        mvc.perform(patch("/api/orders/"+order.getId()+"/status").session(session).header("X-CSRF-TOKEN",csrf)
                        .contentType("application/json").content("{\"targetStatus\":\"PROCESSING\",\"vendorId\":"+sellerB.getId()+",\"adminOverride\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.fulfilment.length()").value(1));
        mvc.perform(get("/api/orders/"+order.getId()).session(session(sellerB)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.fulfilment.length()").value(1));
        mvc.perform(get("/vendor/orders").session(session(sellerB))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Start processing")));
        mvc.perform(get("/api/orders/"+order.getId()).session(session(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fulfilment.length()").value(2));
        mvc.perform(get("/orders").session(session(customer))).andExpect(status().isOk());
    }

    private User user(Role role) {
        return users.saveAndFlush(User.builder().fullName("Fulfilment "+role).email(UUID.randomUUID()+"@example.com")
                .password("unused").role(role).status(AccountStatus.ACTIVE).build());
    }
    private Product product(User seller) {
        Vendor vendor=new Vendor(); vendor.setUser(seller); vendor.setBusinessName("Store");
        vendor.setRegistrationNumber(UUID.randomUUID().toString()); vendor.setStatus(VendorStatus.APPROVED); vendors.saveAndFlush(vendor);
        Product p=new Product(); p.setName("Product "+seller.getId()); p.setBrand("Brand"); p.setCategory("Audio");
        p.setPrice(new BigDecimal("10.00")); p.setStockQuantity(5); p.setVendorId(vendor.getId()); return products.saveAndFlush(p);
    }
    private MockHttpSession session(User user) {
        var details=new CustomUserDetails(user); var session=new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(new UsernamePasswordAuthenticationToken(details,null,details.getAuthorities())));
        return session;
    }
}
