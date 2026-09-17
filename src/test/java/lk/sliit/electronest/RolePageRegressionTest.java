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
