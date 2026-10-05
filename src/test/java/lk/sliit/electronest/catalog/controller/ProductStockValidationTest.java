package lk.sliit.electronest.catalog.controller;

import jakarta.servlet.Filter;
import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class ProductStockValidationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired VendorRepository vendors;
    @Autowired ProductRepository products;
    MockMvc mvc;
    MockHttpSession session;
    Product existing;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        User seller = users.saveAndFlush(User.builder().fullName("Stock seller")
                .email("stock-seller@test.example").password("unused")
                .role(Role.VENDOR).status(AccountStatus.ACTIVE).build());
        Vendor vendor = new Vendor();
        vendor.setUser(seller);
        vendor.setBusinessName("Stock store");
        vendor.setRegistrationNumber("STOCK-TEST");
        vendor.setStatus(VendorStatus.APPROVED);
        vendors.saveAndFlush(vendor);
        existing = new Product();
        existing.setName("Original listing");
        existing.setBrand("Original brand");
        existing.setCategory("Accessories");
        existing.setPrice(BigDecimal.TEN);
        existing.setStockQuantity(5);
        existing.setVendorId(vendor.getId());
        existing.setImageUrl("https://example.com/original.png");
        existing.setImageUrls(List.of(existing.getImageUrl()));
        products.saveAndFlush(existing);
        var details = new CustomUserDetails(seller);
        session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities())));
    }

    @ParameterizedTest
    @CsvSource({"false, 12", "true, 12", "false, 0", "true, 0",
            "false, 2147483647", "true, 2147483647"})
    void validStockIsSaved(boolean editing, String stock) throws Exception {
        long before = products.count();
        MvcResult result = submit(editing, stock);
        assertEquals("/vendor/products", result.getResponse().getRedirectedUrl());
        assertFalse(result.getFlashMap().containsKey("errorMessage"));
        products.flush();
        assertEquals(before + (editing ? 0 : 1), products.count());
        Product saved = editing ? products.findById(existing.getId()).orElseThrow()
                : products.findAll().stream().filter(p -> "Submitted listing".equals(p.getName())).findFirst().orElseThrow();
        assertEquals(Integer.valueOf(stock), saved.getStockQuantity());
        assertEquals("Submitted listing", saved.getName());
    }

    @ParameterizedTest
    @CsvSource({"false, -1", "true, -1", "false, 2147483648", "true, 2147483648",
            "false, 999999999999999999999999", "true, 999999999999999999999999",
            "false, 1.5", "true, 1.5", "false, invalid", "true, invalid", "false, ''", "true, ''"})
    void invalidStockReturnsToFormWithFieldErrorAndDraft(boolean editing, String stock) throws Exception {
        long before = products.count();
        MvcResult result = submit(editing, stock);
        assertEquals(formUrl(editing), result.getResponse().getRedirectedUrl());
        String html = mvc.perform(get(formUrl(editing)).session(session).flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/vendor-product-form"))
                .andExpect(model().attributeHasFieldErrors("product", "stockQuantity"))
                .andExpect(content().string(containsString("https://example.com/submitted.png")))
                .andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("Re-select your image files"));
        assertFalse(result.getFlashMap().containsKey("errorMessage"));
        assertInputAttribute(html, "stockQuantity", "value", stock);
        assertInputAttribute(html, "stockQuantity", "max", "2147483647");
        assertInputAttribute(html, "stockQuantity", "aria-invalid", "true");
        assertInputAttribute(html, "stockQuantity", "aria-describedby", "stockQuantityError");
        assertTrue(Pattern.compile("<p[^>]*id=\"stockQuantityError\"[^>]*>Stock quantity must be a whole number between 0 and 2147483647\\.</p>")
                .matcher(html).find(), "Stock error must be rendered beside the input");
        assertInputAttribute(html, "name", "value", "Submitted listing");
        assertInputAttribute(html, "brand", "value", "Submitted brand");
        assertInputAttribute(html, "category", "value", "Accessories");
        assertTrue(html.contains("Keep this description"));
        assertInputAttribute(html, "price", "value", "25.50");
        assertInputAttribute(html, "ramGb", "value", "16");
        assertInputAttribute(html, "storageGb", "value", "512");
        assertEquals(before, products.count());
        assertEquals(5, products.findById(existing.getId()).orElseThrow().getStockQuantity());
        assertEquals("Original listing", existing.getName());
    }

    private void assertInputAttribute(String html, String id, String attribute, String value) {
        var input = Pattern.compile("<input\\b[^>]*\\bid=\"" + id + "\"[^>]*>").matcher(html);
        assertTrue(input.find(), "Missing input: " + id);
        assertTrue(input.group().contains(attribute + "=\"" + value + "\""),
                () -> "Unexpected " + attribute + " on " + input.group());
    }

    private MvcResult submit(boolean editing, String stock) throws Exception {
        MvcResult page = mvc.perform(get(formUrl(editing)).session(session)).andExpect(status().isOk()).andReturn();
        CsrfToken csrf = (CsrfToken) page.getRequest().getAttribute(CsrfToken.class.getName());
        var request = multipart("/vendor/products/save-media").session(session)
                .param(csrf.getParameterName(), csrf.getToken())
                .param("name", "Submitted listing").param("brand", "Submitted brand")
                .param("category", "Accessories").param("description", "Keep this description")
                .param("price", "25.50").param("stockQuantity", stock)
                .param("ramGb", "16").param("storageGb", "512")
                .param("externalImageUrl", "https://example.com/submitted.png");
        if (editing) request.param("id", existing.getId().toString());
        return mvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
    }

    private String formUrl(boolean editing) {
        return editing ? "/vendor/products/" + existing.getId() + "/edit" : "/vendor/products/new";
    }
}
