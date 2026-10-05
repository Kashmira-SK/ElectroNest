package lk.sliit.electronest.admin.controller;

import jakarta.servlet.Filter;
import lk.sliit.electronest.admin.dto.RegisterForm;
import lk.sliit.electronest.admin.service.RegistrationEmailConflict;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
class RegistrationDuplicateEmailTest {
    private static final String EMAIL = "duplicate-test@example.com";
    private static final String MESSAGE = "An account with this email already exists.";
    @Autowired WebApplicationContext context;
    @Autowired DataSource dataSource;
    @Autowired RegistrationEmailConflict emailConflict;
    @MockitoSpyBean UserRepository users;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach void cleanup() {
        users.findByEmail(EMAIL).ifPresent(users::delete);
    }

    @ParameterizedTest
    @ValueSource(strings = {EMAIL, "DUPLICATE-TEST@EXAMPLE.COM"})
    void ordinaryAndCaseInsensitiveDuplicatesRenderInline(String email) throws Exception {
        existingAccount();
        assertDuplicate(submit(email), email);
    }

    @Test void databaseConflictAfterMissedPrecheckRendersSameInlineError() throws Exception {
        User existing = existingAccount();
        // Simulate another registration committing after this request's pre-check.
        doReturn(false).when(users).existsByEmailIgnoreCase(EMAIL);
        assertDuplicate(submit(EMAIL), EMAIL);
        assertEquals(existing.getId(), users.findByEmail(EMAIL).orElseThrow().getId());
    }

    @Test void unrelatedPersistenceFailureHasSafeFormFeedbackInsteadOfDuplicateEmail() throws Exception {
        doThrow(new DataIntegrityViolationException("SQL private_constraint implementation detail"))
                .when(users).save(any(User.class));
        MvcResult result = submit(EMAIL);
        assertPreserved(result, EMAIL);
        assertFalse(binding(result).hasFieldErrors("email"));
        assertEquals("Unable to create your account right now. Please try again.",
                result.getModelAndView().getModel().get("errorMessage"));
        String html = result.getResponse().getContentAsString();
        assertFalse(html.contains(MESSAGE));
        assertFalse(html.contains("private_constraint"));
        assertFalse(html.contains("implementation detail"));
        assertTrue(users.findByEmail(EMAIL).isEmpty());
    }

    @Test void onlyTheEmailUniqueIndexIsRecognized() throws Exception {
        try (var connection = dataSource.getConnection();
             var indexes = connection.getMetaData().getIndexInfo(
                     connection.getCatalog(), connection.getSchema(), "users", true, false)) {
            boolean testedEmail = false;
            boolean testedOther = false;
            while (indexes.next()) {
                String index = indexes.getString("INDEX_NAME");
                if (index == null) continue;
                boolean email = "email".equals(indexes.getString("COLUMN_NAME"));
                // PostgreSQL-style bare constraint name, distinct from H2's descriptive name.
                assertEquals(email, emailConflict.matches(conflict("23505", index)));
                assertFalse(emailConflict.matches(conflict("23502", index)));
                testedEmail |= email;
                testedOther |= !email;
            }
            assertTrue(testedEmail);
            assertTrue(testedOther);
        }
        assertFalse(emailConflict.matches(conflict("23505", "unrelated_unique_constraint")));
        assertFalse(emailConflict.matches(conflict("23505", null)));
    }

    @Test void validRegistrationStillSucceeds() throws Exception {
        MvcResult result = submit(EMAIL);
        assertEquals("/login", result.getResponse().getRedirectedUrl());
        assertTrue(result.getFlashMap().containsKey("successMessage"));
        assertEquals("Submitted customer", users.findByEmail(EMAIL).orElseThrow().getFullName());
    }

    private Throwable conflict(String sqlState, String constraint) {
        return new DataIntegrityViolationException("private database details",
                new ConstraintViolationException("private constraint details",
                        new SQLException("private SQL details", sqlState), constraint));
    }

    private User existingAccount() {
        return users.saveAndFlush(User.builder().fullName("Existing customer").email(EMAIL)
                .password("existing hash").role(Role.CUSTOMER).build());
    }

    private void assertDuplicate(MvcResult result, String email) throws Exception {
        assertPreserved(result, email);
        assertEquals(MESSAGE, binding(result).getFieldError("email").getDefaultMessage());
        assertFalse(result.getModelAndView().getModel().containsKey("errorMessage"));
        String html = result.getResponse().getContentAsString();
        assertTrue(Pattern.compile("<input[^>]*id=\"email\"[^>]*>\\s*<p class=\"en-field-error\"[^>]*>"
                + Pattern.quote(MESSAGE) + "</p>").matcher(html).find());
        assertEquals(1, html.split(Pattern.quote(MESSAGE), -1).length - 1);
        for (String detail : new String[]{"SQL", "constraint", "Exception", "org.hibernate", "23505"}) {
            assertFalse(html.contains(detail), detail);
        }
    }

    private void assertPreserved(MvcResult result, String email) throws Exception {
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("auth/register", result.getModelAndView().getViewName());
        var form = (RegisterForm) result.getModelAndView().getModel().get("registerForm");
        assertEquals("Submitted customer", form.getFullName());
        assertEquals(email, form.getEmail());
        assertEquals("0771234567", form.getContactNumber());
        assertNull(form.getPassword());
        assertNull(form.getConfirmPassword());
        String html = result.getResponse().getContentAsString();
        for (String value : new String[]{"Submitted customer", email, "0771234567"}) {
            assertTrue(html.contains("value=\"" + value + "\""));
        }
        for (String field : new String[]{"password", "confirmPassword"}) {
            var input = Pattern.compile("<input[^>]*id=\"" + field + "\"[^>]*>").matcher(html);
            assertTrue(input.find());
            assertTrue(input.group().contains("value=\"\""));
        }
        assertFalse(html.contains("Secret123!"));
    }

    private BindingResult binding(MvcResult result) {
        return (BindingResult) result.getModelAndView().getModel()
                .get(BindingResult.MODEL_KEY_PREFIX + "registerForm");
    }

    private MvcResult submit(String email) throws Exception {
        var session = new MockHttpSession();
        var page = mvc.perform(get("/register").session(session)).andExpect(status().isOk()).andReturn();
        var csrf = (CsrfToken) page.getRequest().getAttribute(CsrfToken.class.getName());
        return mvc.perform(post("/register").session(session).param(csrf.getParameterName(), csrf.getToken())
                .param("fullName", "Submitted customer").param("email", email)
                .param("contactNumber", "0771234567").param("password", "Secret123!")
                .param("confirmPassword", "Secret123!")).andReturn();
    }
}
