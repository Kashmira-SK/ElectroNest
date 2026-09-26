package lk.sliit.electronest;

import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.model.*;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.cart.model.CartItem;
import lk.sliit.electronest.cart.repository.CartItemRepository;
import lk.sliit.electronest.order.model.*;
import lk.sliit.electronest.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class RolePageRegressionTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired VendorRepository vendors;
    @Autowired ProductRepository products;
    @Autowired CartItemRepository carts;
    @Autowired OrderRepository orders;
    MockMvc mvc;
    User customer, seller, admin;
    Product product;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        customer = user(Role.CUSTOMER);
        seller = user(Role.VENDOR);
        admin = user(Role.ADMIN);
        Vendor vendor = new Vendor();
        vendor.setUser(seller);
        vendor.setBusinessName("Regression Store");
        vendor.setRegistrationNumber("REG-TEST");
        vendor.setStatus(VendorStatus.APPROVED);
        vendors.saveAndFlush(vendor);
        product = new Product();
        product.setName("Regression Product");
        product.setBrand("Brand");
        product.setCategory("Accessories");
        product.setPrice(BigDecimal.TEN);
        product.setStockQuantity(5);
        product.setVendorId(vendor.getId());
        product.setImageUrl("/images/test.jpg");
        product.setImageUrls(List.of("/images/test.jpg", "/images/second.jpg"));
        products.saveAndFlush(product);
        carts.save(new CartItem(customer.getId(), product.getId(), 1, BigDecimal.TEN));
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveryName("Customer");
        order.setDeliveryPhone("0771234567");
        order.setAddressLine1("Test street");
        order.setCity("Colombo");
        order.setCountry("Sri Lanka");
        OrderLineItem item = new OrderLineItem();
        item.setVendor(seller);
        item.setProductId(product.getId());
        item.setUnitPrice(BigDecimal.TEN);
        item.setQuantity(1);
        order.addLineItem(item);
        orders.saveAndFlush(order);
    }

    @Test void publicPagesRenderWithoutLogin() throws Exception {
        for (String path : List.of("/", "/products", "/products/" + product.getId(), "/login", "/register")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/search/products")).andExpect(status().isOk());
        mvc.perform(get("/api/orders/1")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/products/999999999")).andExpect(status().isNotFound())
                .andExpect(view().name("catalog/product-unavailable"));
    }

    @Test void customerPagesRenderWithPersistedData() throws Exception {
        check(customer, "/cart", "/checkout", "/orders", "/settings");
    }

    @Test void sellerPagesRenderWithOwnedProductsAndOrders() throws Exception {
        check(seller, "/vendor/products", "/vendor/products/new", "/vendor/products/" + product.getId() + "/edit",
                "/vendor/orders", "/vendor/profile", "/settings");
    }

    @Test void lowStockApiAndSummaryAreScopedToAuthenticatedVendor() throws Exception {
        product.setStockQuantity(0);
        products.saveAndFlush(product);
        stockProduct("Own low stock", product.getVendorId(), 4);
        stockProduct("Own threshold stock", product.getVendorId(), 5);
        User otherSeller = User.builder().fullName("Other seller").email("other-seller@regression.test")
                .password("unused").role(Role.VENDOR).status(AccountStatus.ACTIVE).build();
        users.saveAndFlush(otherSeller);
        Vendor other = new Vendor();
        other.setUser(otherSeller);
        other.setBusinessName("Other store");
        other.setRegistrationNumber("OTHER-REG");
        other.setStatus(VendorStatus.APPROVED);
        vendors.saveAndFlush(other);
        stockProduct("Other private low stock", other.getId(), 1);

        mvc.perform(get("/api/products/low-stock").param("vendorId", other.getId().toString()).session(session(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", org.hamcrest.Matchers.containsInAnyOrder("Regression Product", "Own low stock")))
                .andExpect(jsonPath("$[*].stockQuantity", org.hamcrest.Matchers.containsInAnyOrder(0, 4)));
        mvc.perform(get("/api/products/low-stock").param("threshold", "1").session(session(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stockQuantity").value(0));
        mvc.perform(get("/vendor/products").session(session(seller)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("lowStockProducts", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Out of stock")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("#stock-" + product.getId())))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Other private low stock"))));
    }

    @Test void healthyStockHidesWarningsAndNegativeThresholdIsRejected() throws Exception {
        mvc.perform(get("/vendor/products").session(session(seller)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stock levels healthy.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"stock-warning-title\""))));
        mvc.perform(get("/api/products/low-stock").param("threshold", "-1").session(session(seller)))
                .andExpect(status().isBadRequest());
    }

    private void stockProduct(String name, Long vendorId, int quantity) {
        Product item = new Product();
        item.setName(name);
        item.setBrand("Brand");
        item.setCategory("Accessories");
        item.setPrice(BigDecimal.TEN);
        item.setStockQuantity(quantity);
        item.setVendorId(vendorId);
        products.saveAndFlush(item);
    }

    @Test void sellerFormsExposeMatchingFieldLimits() throws Exception {
        mvc.perform(get("/vendor/products/new").session(session(seller)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("minlength=\"2\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("maxlength=\"255\"")));
        mvc.perform(get("/vendor/register").session(session(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("maxlength=\"255\"")));
    }

    @Test void missingPaymentsAndReceiptsReturnNotFound() throws Exception {
        for (String path : List.of("/payment?orderId=999999999", "/receipt?paymentId=999999999",
                "/api/v1/payments/999999999", "/api/v1/receipts/999999999")) {
            mvc.perform(get(path).session(session(customer))).andExpect(status().isNotFound());
        }
    }

    @Test void anotherCustomersOrderIsForbiddenOnPaymentPage() throws Exception {
        User other = User.builder().fullName("Other").email("other@regression.test")
                .password("unused").role(Role.CUSTOMER).status(AccountStatus.ACTIVE).build();
        users.saveAndFlush(other);
        Long orderId = orders.findByCustomer_Id(customer.getId()).getFirst().getId();
        mvc.perform(get("/payment").param("orderId", orderId.toString()).session(session(other)))
                .andExpect(status().isForbidden()).andExpect(view().name("error/access-denied"));
    }

    @Test void hardwareFiltersComposeWithKeywordAndExcludeUnknownSpecs() throws Exception {
        product.setRamGb(16); product.setStorageGb(512); products.saveAndFlush(product);
        stockProduct("Unknown specs", product.getVendorId(), 4);
        mvc.perform(get("/api/search/products").param("keyword","Regression").param("minRamGb","16").param("minStorageGb","512"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ramGb").value(16));
        mvc.perform(get("/api/search/products").param("minRamGb","32"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/search/products").param("minStorageGb","512"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for (String invalid : List.of("0", "-1", "1.5", "4097", "1 OR 1=1")) {
            mvc.perform(get("/api/search/products").param("minRamGb",invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/products/"+product.getId())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("512 GB")));
        mvc.perform(get("/vendor/products/"+product.getId()+"/edit").session(session(seller)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"16\"")));
    }

    @Test void sellerCanPersistSpecsButInvalidSpecsDoNotChangeProduct() throws Exception {
        var sellerSession = session(seller);
        var page = mvc.perform(get("/vendor/products/"+product.getId()+"/edit").session(sellerSession)).andReturn();
        String token = ((org.springframework.security.web.csrf.CsrfToken) page.getRequest()
                .getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName())).getToken();
        String payload = "{\"name\":\"Regression Product\",\"brand\":\"Brand\",\"category\":\"Audio\",\"price\":10,\"stockQuantity\":5,\"ramGb\":16,\"storageGb\":512}";
        mvc.perform(put("/api/products/"+product.getId()).session(sellerSession).header("X-CSRF-TOKEN",token)
                        .contentType("application/json").content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ramGb").value(16)).andExpect(jsonPath("$.storageGb").value(512));
        mvc.perform(put("/api/products/"+product.getId()).session(sellerSession).header("X-CSRF-TOKEN",token)
                        .contentType("application/json").content(payload.replace("\"ramGb\":16", "\"ramGb\":-1")))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(16, products.findById(product.getId()).orElseThrow().getRamGb());
    }

    @Test void reportRangeExportAndAccessRules() throws Exception {
        mvc.perform(get("/admin/reports").param("from", "2026-01-01").param("until", "2026-01-02").session(session(admin)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Download CSV")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("N/A (zero baseline)")));
        mvc.perform(get("/admin/reports/export").param("from", "2026-01-01").param("until", "2026-01-02").session(session(admin)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-01-02,0,0,0,0")));
        mvc.perform(get("/admin/reports/export").param("from", "2026-01-01").param("until", "2026-01-02").session(session(customer)))
                .andExpect(redirectedUrl("/access-denied"));
        mvc.perform(get("/admin/reports/export").param("from", "2026-01-02").param("until", "2026-01-01").session(session(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/reports").param("from", "2026-01-02").param("until", "2026-01-01").session(session(admin)))
                .andExpect(status().isOk()).andExpect(model().attributeExists("reportError"));
    }

    @Test void adminPagesRender() throws Exception {
        check(admin, "/admin/dashboard", "/admin/users", "/admin/users/" + customer.getId(),
                "/vendor/queue", "/vendor/manage", "/admin/reports", "/admin/reviews", "/admin/audit-logs", "/settings");
    }

    @Test void roleBoundariesAndCsrfBlockUnintendedActions() throws Exception {
        mvc.perform(get("/admin/dashboard").session(session(customer)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/access-denied"));
        mvc.perform(post("/api/products").servletPath("/api/products").session(session(seller))
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/vendor/my-products").session(session(customer)))
                .andExpect(status().isForbidden());
    }

    void check(User user, String... paths) throws Exception {
        for (String path : paths) mvc.perform(get(path).session(session(user))).andExpect(status().isOk());
    }

    MockHttpSession session(User user) {
        var details = new CustomUserDetails(user);
        var authentication = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(authentication));
        return session;
    }

    User user(Role role) {
        User user = User.builder().fullName("Regression " + role).email(role + "@regression.test")
                .password("unused").role(role).status(AccountStatus.ACTIVE).contactNumber("0771234567").build();
        user.setDeliveryName(user.getFullName());
        user.setDeliveryPhone("0771234567");
        user.setDeliveryAddressLine1("Test street");
        user.setDeliveryCity("Colombo");
        user.setDeliveryCountry("Sri Lanka");
        return users.saveAndFlush(user);
    }
}
