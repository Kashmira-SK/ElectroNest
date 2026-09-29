package lk.sliit.electronest.search.controller;

import jakarta.servlet.Filter;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.order.model.*;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.search.model.Review;
import lk.sliit.electronest.search.repository.ReviewRepository;
import lk.sliit.electronest.search.service.ReviewImageStorageService;
import lk.sliit.electronest.vendor.model.*;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class ReviewPhotoFlowTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired VendorRepository vendors;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewImageStorageService storage;
    MockMvc mvc;
    User customer, seller;
    Product product;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        customer = user(Role.CUSTOMER);
        seller = user(Role.VENDOR);
        Vendor vendor = new Vendor();
        vendor.setUser(seller);
        vendor.setBusinessName("Photo Store");
        vendor.setRegistrationNumber(UUID.randomUUID().toString());
        vendor.setStatus(VendorStatus.APPROVED);
        vendors.saveAndFlush(vendor);
        product = new Product();
        product.setName("Review photo product");
        product.setBrand("Brand");
        product.setCategory("Accessories");
        product.setPrice(BigDecimal.TEN);
        product.setStockQuantity(5);
        product.setVendorId(vendor.getId());
        products.saveAndFlush(product);
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

    @AfterEach void cleanup() {
        reviews.findByCustomerIdAndProductId(customer.getId(), product.getId())
                .ifPresent(review -> storage.deleteUrlQuietly(review.getPhotoPath()));
    }

    User user(Role role) {
        return users.saveAndFlush(User.builder().fullName("Photo test user").email(UUID.randomUUID() + "@test.example")
                .password("unused").role(role).status(AccountStatus.ACTIVE).build());
    }

    MockHttpSession session(User user) {
        var principal = new CustomUserDetails(user);
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        return session;
    }

    String csrf(MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/").session(session)).andReturn();
        return ((CsrfToken) result.getRequest().getAttribute(CsrfToken.class.getName())).getToken();
    }

    MockMultipartFile photo() {
        return new MockMultipartFile("photo", "photo.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10});
    }

    String api() { return "/api/review-photos/product/" + product.getId(); }
    Review review() { return reviews.findByCustomerIdAndProductId(customer.getId(), product.getId()).orElseThrow(); }

    MockMultipartHttpServletRequestBuilder upload(String url, User user, MockMultipartFile file) throws Exception {
        var session = session(user);
        var request = multipart(url);
        if (file != null) request.file(file);
        request.servletPath(url).accept(MediaType.APPLICATION_JSON).session(session).param("_csrf", csrf(session)).param("rating", "5").param("reviewText", "Great product");
        return request;
    }

    @Test void multipartCreateWithoutPhoto() throws Exception {
        mvc.perform(upload(api(), customer, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.photoPath").doesNotExist()).andExpect(jsonPath("$.verified").value(true));
    }

    @Test void multipartCreateServeDisplayPreserveAndReplacePhoto() throws Exception {
        mvc.perform(upload(api(), customer, photo())).andExpect(status().isOk())
                .andExpect(jsonPath("$.photoPath", startsWith(ReviewImageStorageService.URL_PREFIX)));
        String old = review().getPhotoPath();
        mvc.perform(get(old)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(photo().getBytes()));
        mvc.perform(get("/products/" + product.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"" + old + "\"")));
        mvc.perform(get("/orders").session(session(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("name=\"photo\"")));
        mvc.perform(upload(api() + "/" + review().getId(), customer, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.photoPath").value(old));
        mvc.perform(upload(api() + "/" + review().getId(), customer, photo())).andExpect(status().isOk())
                .andExpect(jsonPath("$.photoPath", not(old)));
        mvc.perform(get(old)).andExpect(status().isNotFound());
        mvc.perform(get(review().getPhotoPath())).andExpect(status().isOk());
    }

    @Test void normalBrowserFormUploadsAndRedirects() throws Exception {
        mvc.perform(get("/products/" + product.getId()).session(session(customer)))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("name=\"photo\"")));
        mvc.perform(upload("/products/" + product.getId() + "/reviews", customer, photo()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        String original = review().getPhotoPath();
        assertNotNull(original);
        mvc.perform(upload("/products/" + product.getId() + "/reviews/" + review().getId(), customer, null))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        assertEquals(original, review().getPhotoPath());
    }

    @Test void invalidImageIsNormalValidationErrorForBothFlows() throws Exception {
        var invalid = new MockMultipartFile("photo", "bad.svg", "image/svg+xml", "<svg/>".getBytes());
        mvc.perform(upload(api(), customer, invalid)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("JPG")));
        mvc.perform(upload("/products/" + product.getId() + "/reviews", customer, invalid))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
        assertTrue(reviews.findByCustomerIdAndProductId(customer.getId(), product.getId()).isEmpty());
    }

    @Test void securityRoleOwnershipAndCsrfRemainEnforced() throws Exception {
        mvc.perform(upload(api(), seller, photo())).andExpect(status().isForbidden());
        mvc.perform(multipart(api()).file(photo()).servletPath(api()).param("rating", "5").session(session(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(upload(api(), customer, photo())).andExpect(status().isOk());
        String old = review().getPhotoPath();
        mvc.perform(upload(api() + "/" + review().getId(), user(Role.CUSTOMER), photo()))
                .andExpect(status().isForbidden());
        assertEquals(old, review().getPhotoPath());
        mvc.perform(get(old)).andExpect(status().isOk());
    }

    @Test void anonymousUploadCannotCreateReview() throws Exception {
        var guest = new MockHttpSession();
        mvc.perform(multipart(api()).file(photo()).servletPath(api()).session(guest)
                .param("_csrf", csrf(guest)).param("rating", "5"))
                .andExpect(status().is3xxRedirection());
        assertTrue(reviews.findByCustomerIdAndProductId(customer.getId(), product.getId()).isEmpty());
    }

    @Test void deletingBrowserReviewRemovesItsPhoto() throws Exception {
        mvc.perform(upload(api(), customer, photo())).andExpect(status().isOk());
        String old = review().getPhotoPath();
        var session = session(customer);
        String url = "/api/review-photos/" + review().getId();
        mvc.perform(delete(url).servletPath(url).session(session).header("X-CSRF-TOKEN", csrf(session)))
                .andExpect(status().isNoContent());
        mvc.perform(get(old)).andExpect(status().isNotFound());
        assertTrue(reviews.findByCustomerIdAndProductId(customer.getId(), product.getId()).isEmpty());
    }

    @Test void existingJsonApiStillAcceptsPhotoPath() throws Exception {
        var session = session(customer);
        mvc.perform(post("/api/reviews/product/" + product.getId()).session(session)
                .header("X-CSRF-TOKEN", csrf(session)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":4,\"reviewText\":\"JSON review\",\"photoPath\":\"https://example.com/photo.png\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photoPath").value("https://example.com/photo.png"));
    }
}
