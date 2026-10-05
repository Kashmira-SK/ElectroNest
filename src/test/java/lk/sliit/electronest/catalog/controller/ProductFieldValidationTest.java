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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.validation.BindingResult;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class ProductFieldValidationTest {
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

    static Stream<Arguments> invalidFields() {
        List<String[]> cases = List.of(
                new String[]{"name", "", "Product name is required"},
                new String[]{"name", "x", "Product name must be between"},
                new String[]{"name", "x".repeat(151), "Product name must be between"},
                new String[]{"brand", "", "Brand is required"},
                new String[]{"brand", "x".repeat(256), "Brand must be 255"},
                new String[]{"category", "", "Category is required"},
                new String[]{"category", "x".repeat(256), "Category must be 255"},
                new String[]{"description", "x".repeat(1001), "Description cannot exceed"},
                new String[]{"price", "", "Price is required"},
                new String[]{"price", "not-a-price", "Enter a valid price"},
                new String[]{"price", "0", "Price must be greater than zero"},
                new String[]{"price", "-1", "Price must be greater than zero"},
                new String[]{"price", "1.234", "Price must have at most two decimal places"},
                new String[]{"price", "25.5000", "Price must have at most two decimal places"},
                new String[]{"price", "9".repeat(37), "Price must have at most two decimal places"},
                new String[]{"stockQuantity", "2147483648", "Stock quantity must be a whole number"},
                new String[]{"stockQuantity", "1.5", "Stock quantity must be a whole number"},
                new String[]{"ramGb", "-1", "RAM must"},
                new String[]{"ramGb", "4097", "RAM must"},
                new String[]{"ramGb", "invalid", "RAM must be a whole number"},
                new String[]{"storageGb", "-1", "Storage must"},
                new String[]{"storageGb", "1048577", "Storage must"},
                new String[]{"storageGb", "1.5", "Storage must be a whole number"});
        return Stream.of("multipart-create", "multipart-edit", "legacy-create", "legacy-edit")
                .flatMap(route -> cases.stream().map(c -> Arguments.of(route, c[0], c[1], c[2])));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void errorsAreInlineAndDraftIsRetained(String route, String field, String value, String message) throws Exception {
        var values = values();
        values.put(field, value);
        long before = products.count();
        MvcResult submitted = submit(route, values);
        MvcResult rendered = render(route, submitted);
        String html = rendered.getResponse().getContentAsString();
        var errors = (BindingResult) rendered.getModelAndView().getModel().get(BindingResult.MODEL_KEY_PREFIX + "product");
        assertTrue(errors.hasFieldErrors(field));
        assertFalse(errors.hasGlobalErrors(), "Field errors should not repeat in a banner");
        assertFalse(rendered.getModelAndView().getModel().containsKey("errorMessage"));
        assertInlineError(html, field, message);
        for (var entry : values.entrySet()) assertValue(html, entry.getKey(), entry.getValue());
        assertFalse(html.contains("Re-select your image files"));
        assertFalse(html.contains("Failed to convert"));
        assertFalse(html.contains("NumberFormatException"));
        assertEquals(before, products.count());
        assertEquals("Original listing", existing.getName());
        assertEquals(5, existing.getStockQuantity());
        if (route.endsWith("edit")) assertTrue(html.contains("https://example.com/original.png"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"multipart-create", "multipart-edit", "legacy-create", "legacy-edit"})
    void multipleRequiredErrorsAppearTogether(String route) throws Exception {
        var values = values();
        for (String field : List.of("name", "brand", "category", "price", "stockQuantity")) values.put(field, "");
        String html = render(route, submit(route, values)).getResponse().getContentAsString();
        for (String field : List.of("name", "brand", "category", "price", "stockQuantity")) {
            assertInlineError(html, field, field.equals("stockQuantity") ? "Stock quantity" : "required");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"multipart-create", "multipart-edit", "legacy-create", "legacy-edit"})
    void validCreateAndEditStillSave(String route) throws Exception {
        long before = products.count();
        MvcResult result = submit(route, values());
        assertEquals("/vendor/products", result.getResponse().getRedirectedUrl());
        assertFalse(result.getFlashMap().containsKey(BindingResult.MODEL_KEY_PREFIX + "product"));
        products.flush();
        assertEquals(before + (route.endsWith("edit") ? 0 : 1), products.count());
        Product saved = route.endsWith("edit") ? existing : products.findAll().stream()
                .filter(p -> "Submitted listing".equals(p.getName())).findFirst().orElseThrow();
        assertEquals("Submitted listing", saved.getName());
        assertEquals(0, saved.getStockQuantity());
        assertEquals(16, saved.getRamGb());
    }

    @ParameterizedTest
    @ValueSource(strings = {"multipart-create", "multipart-edit"})
    void imageUrlErrorIsBesideUrl(String route) throws Exception {
        var values = values();
        values.put("externalImageUrl", "file:///bad.png");
        String html = render(route, submit(route, values)).getResponse().getContentAsString();
        assertInlineError(html, "externalImageUrl", "Enter a valid HTTPS or HTTP image URL");
        assertValue(html, "externalImageUrl", "file:///bad.png");
        assertFalse(html.contains("Re-select your image files"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"multipart-create", "multipart-edit"})
    void imageUploadErrorAndReselectionAreBesideUpload(String route) throws Exception {
        var file = new MockMultipartFile("images", "bad.txt", "text/plain", "bad".getBytes());
        String html = render(route, submit(route, values(), file)).getResponse().getContentAsString();
        assertTrue(html.contains("id=\"productImagesError\""));
        assertTrue(html.contains("Product images must be JPG, PNG or WEBP"));
        assertTrue(html.contains("Re-select your image files"));
        assertFalse(html.contains("Could not save the product"));
        assertValue(html, "name", "Submitted listing");
        assertEquals("Original listing", existing.getName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"multipart-create", "multipart-edit"})
    void reselectNoticeAppearsWhenTextErrorsPreventProcessingUpload(String route) throws Exception {
        var values = values();
        values.put("brand", "");
        var file = new MockMultipartFile("images", "image.png", "image/png", new byte[]{1});
        String html = render(route, submit(route, values, file)).getResponse().getContentAsString();
        assertInlineError(html, "brand", "Brand is required");
        assertTrue(html.contains("Re-select your image files"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"multipart-edit", "legacy-edit"})
    void imageRemovalSelectionSurvivesInvalidEdit(String route) throws Exception {
        var values = values();
        values.put("brand", "");
        values.put("removeImages", existing.getImageUrl());
        String html = render(route, submit(route, values)).getResponse().getContentAsString();
        var checkbox = Pattern.compile("<input[^>]*name=\"removeImages\"[^>]*>").matcher(html);
        assertTrue(checkbox.find());
        assertTrue(checkbox.group().contains("checked=\"checked\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"multipart-create", "multipart-edit"})
    void existingNameTrimmingIsPreserved(String route) throws Exception {
        var values = values();
        values.put("name", "  Submitted listing  ");
        MvcResult result = submit(route, values);
        assertEquals("/vendor/products", result.getResponse().getRedirectedUrl());
        assertFalse(result.getFlashMap().containsKey(BindingResult.MODEL_KEY_PREFIX + "product"));
        products.flush();
    }

    @ParameterizedTest
    @CsvSource({"multipart-create, 9", "multipart-edit, 9", "multipart-create, 8", "multipart-edit, 8"})
    void imageCountErrorsStayWithGallery(String route, int count) throws Exception {
        var files = new MockMultipartFile[count];
        for (int i = 0; i < count; i++) files[i] = new MockMultipartFile("images", "image.png", "image/png", new byte[]{1});
        String html = render(route, submit(route, values(), files)).getResponse().getContentAsString();
        assertTrue(html.contains("id=\"productImagesError\""));
        assertTrue(html.contains(count > 8 ? "You can upload up to 8 product images." : "Use up to 8 images in total, including the external URL."));
        assertTrue(html.contains("Re-select your image files"));
        assertEquals("Original listing", existing.getName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"multipart-create", "multipart-edit"})
    void oversizeFileErrorStaysWithUpload(String route) throws Exception {
        var file = new MockMultipartFile("images", "image.png", "image/png", new byte[]{1}) {
            @Override public long getSize() { return 8L * 1024 * 1024 + 1; }
        };
        String html = render(route, submit(route, values(), file)).getResponse().getContentAsString();
        assertTrue(html.contains("id=\"productImagesError\""));
        assertTrue(html.contains("Each product image must be 8 MB or smaller"));
        assertTrue(html.contains("Re-select your image files"));
    }

    @org.junit.jupiter.api.Test
    void missingCreateImageExplainsRequirementWithoutReselectionNotice() throws Exception {
        var values = values();
        values.put("externalImageUrl", "");
        String html = render("multipart-create", submit("multipart-create", values)).getResponse().getContentAsString();
        assertTrue(html.contains("Add at least one product image or image URL."));
        assertFalse(html.contains("Re-select your image files"));
    }

    private Map<String, String> values() {
        return new LinkedHashMap<>(Map.of("name", "Submitted listing", "brand", "Submitted brand",
                "category", "Accessories", "description", "Keep this description", "price", "25.50",
                "stockQuantity", "0", "ramGb", "16", "storageGb", "512",
                "externalImageUrl", "https://example.com/submitted.png"));
    }

    private MvcResult submit(String route, Map<String, String> values, MockMultipartFile... files) throws Exception {
        boolean editing = route.endsWith("edit");
        MvcResult page = mvc.perform(get(formUrl(editing)).session(session)).andExpect(status().isOk()).andReturn();
        CsrfToken csrf = (CsrfToken) page.getRequest().getAttribute(CsrfToken.class.getName());
        AbstractMockHttpServletRequestBuilder<?> request;
        if (route.startsWith("multipart")) {
            var multipart = multipart("/vendor/products/save-media");
            for (var file : files) multipart.file(file);
            if (editing) multipart.param("id", existing.getId().toString());
            request = multipart;
        } else {
            request = post(editing ? "/vendor/products/" + existing.getId() : "/vendor/products");
        }
        request.session(session).param(csrf.getParameterName(), csrf.getToken());
        values.forEach(request::param);
        return mvc.perform(request).andReturn();
    }

    private MvcResult render(String route, MvcResult result) throws Exception {
        if (route.startsWith("multipart")) {
            assertEquals(formUrl(route.endsWith("edit")), result.getResponse().getRedirectedUrl());
            result = mvc.perform(get(result.getResponse().getRedirectedUrl()).session(session)
                    .flashAttrs(result.getFlashMap())).andExpect(status().isOk()).andReturn();
        }
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("catalog/vendor-product-form", result.getModelAndView().getViewName());
        return result;
    }

    private void assertInlineError(String html, String field, String message) {
        var error = Pattern.compile("<p[^>]*id=\"" + field + "Error\"[^>]*>(.*?)</p>", Pattern.DOTALL).matcher(html);
        assertTrue(error.find(), "Missing inline error: " + field);
        assertTrue(error.group(1).contains(message), error.group(1));
        assertTrue(html.contains("aria-describedby=\"" + field + "Error\""));
    }

    private void assertValue(String html, String field, String value) {
        if (field.equals("description")) {
            assertTrue(html.contains(value));
        } else {
            var input = Pattern.compile("<input[^>]*id=\"" + field + "\"[^>]*>").matcher(html);
            assertTrue(input.find(), "Missing input: " + field);
            assertTrue(input.group().contains("value=\"" + value + "\""), input.group());
        }
    }

    private String formUrl(boolean editing) {
        return editing ? "/vendor/products/" + existing.getId() + "/edit" : "/vendor/products/new";
    }
}
