package lk.sliit.electronest.vendor.service;

import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.Filter;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class VendorRegistrationGuidanceTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired VendorRepository vendors;
    @Autowired VendorDocumentStorageService storage;
    MockMvc mvc;
    MockHttpSession session;
    User user;
    String csrf;

    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        user = users.saveAndFlush(User.builder().fullName("Applicant").email(UUID.randomUUID() + "@test.example")
                .password("unused").role(Role.CUSTOMER).build());
        var principal = new CustomUserDetails(user);
        session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        var page = mvc.perform(get("/vendor/register").session(session)).andExpect(status().isOk()).andReturn();
        csrf = ((CsrfToken) page.getRequest().getAttribute(CsrfToken.class.getName())).getToken();
    }

    @Test void missingRequiredFieldsAndDocumentReturnFieldErrors() throws Exception {
        mvc.perform(multipart("/vendor/register").session(session).param("_csrf", csrf))
                .andExpect(status().isOk()).andExpect(view().name("vendor/register"))
                .andExpect(model().attributeHasFieldErrors("registrationRequest", "businessName", "registrationNumber", "businessAddress", "contactPhone"))
                .andExpect(content().string(containsString("Verification document is required")));
        assertFalse(vendors.existsByUser_Id(user.getId()));
    }

    @Test void malformedPhonePreservesOtherFieldsAndShowsFormat() throws Exception {
        mvc.perform(application("077-123-4567").file(document("proof.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isOk()).andExpect(view().name("vendor/register"))
                .andExpect(model().attributeHasFieldErrors("registrationRequest", "contactPhone"))
                .andExpect(content().string(containsString("value=\"My Business\"")))
                .andExpect(content().string(containsString("no spaces or hyphens")));
    }

    @Test void invalidAndOversizedFilesReturnBesideInputWithoutCreatingApplication() throws Exception {
        for (var file : new MockMultipartFile[]{
                document("proof.exe", "application/octet-stream", new byte[]{1}),
                document("proof.pdf", "application/pdf", new byte[15 * 1024 * 1024 + 1])}) {
            mvc.perform(application("0771234567").file(file))
                    .andExpect(status().isOk()).andExpect(view().name("vendor/register"))
                    .andExpect(model().attributeExists("documentValidationError"))
                    .andExpect(content().string(containsString("value=\"My Business\"")));
        }
        assertFalse(vendors.existsByUser_Id(user.getId()));
    }

    @Test void validApplicationStoresDocumentAndUsesAuthenticatedApplicant() throws Exception {
        try {
            mvc.perform(application("+94771234567").param("userId", "999999")
                            .file(document("proof.pdf", "application/pdf", "%PDF-1.7\nproof".getBytes())))
                    .andExpect(status().isOk()).andExpect(view().name("vendor/register-success"));
            var vendor = vendors.findByUser_Id(user.getId()).orElseThrow();
            assertEquals("REG-123", vendor.getRegistrationNumber());
            assertNotNull(vendor.getIdDocumentPath());
            assertTrue(storage.load(vendor.getIdDocumentPath()).exists());
        } finally {
            vendors.findByUser_Id(user.getId()).ifPresent(vendor -> storage.deleteQuietly(vendor.getIdDocumentPath()));
        }
    }

    private MockMultipartHttpServletRequestBuilder application(String phone) {
        var request = multipart("/vendor/register");
        request.session(session).param("_csrf", csrf).param("businessName", "My Business")
                .param("registrationNumber", "reg-123").param("businessAddress", "42 Galle Road")
                .param("contactPhone", phone);
        return request;
    }

    private MockMultipartFile document(String filename, String type, byte[] bytes) {
        return new MockMultipartFile("document", filename, type, bytes);
    }
}
