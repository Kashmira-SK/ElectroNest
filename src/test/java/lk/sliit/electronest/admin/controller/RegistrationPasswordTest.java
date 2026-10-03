package lk.sliit.electronest.admin.controller;

import jakarta.servlet.Filter;
import lk.sliit.electronest.admin.dto.RegisterForm;
import lk.sliit.electronest.common.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.stream.Stream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class RegistrationPasswordTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    static Stream<String> validPasswords() {
        return Stream.of("abcdef", "Secret123!", "a".repeat(72), "é".repeat(35), "é".repeat(36), "😀".repeat(18));
    }

    static Stream<String> oversizedPasswords() {
        return Stream.of("a".repeat(73), "é".repeat(37), "😀".repeat(19));
    }

    @ParameterizedTest
    @MethodSource("validPasswords")
    void supportedPasswordsRegisterWithoutChangingTheirValue(String password) throws Exception {
        MvcResult result = submit(password, password);
        assertEquals("/login", result.getResponse().getRedirectedUrl());
        assertTrue(result.getFlashMap().containsKey("successMessage"));
        var user = users.findByEmail("password-test@example.com").orElseThrow();
        assertTrue(encoder.matches(password, user.getPassword()));
    }

    @ParameterizedTest
    @MethodSource("oversizedPasswords")
    void oversizedPasswordsReturnInlineErrorBeforeEncoding(String password) throws Exception {
        MvcResult result = submit(password, password);
        assertRejected(result);
        var binding = (org.springframework.validation.BindingResult) result.getModelAndView().getModel()
                .get(org.springframework.validation.BindingResult.MODEL_KEY_PREFIX + "registerForm");
        assertEquals("Password must not exceed 72 bytes.", binding.getFieldError("password").getDefaultMessage());
        assertFalse(result.getModelAndView().getModel().containsKey("errorMessage"));
        String html = result.getResponse().getContentAsString();
        assertTrue(Pattern.compile("<p[^>]*id=\"passwordError\"[^>]*>Password must not exceed 72 bytes\\.</p>")
                .matcher(html).find());
        assertTrue(html.contains("aria-describedby=\"passwordHint passwordError\""));
        assertTrue(html.contains("no more than 72 UTF-8 bytes"));
        assertTrue(html.contains("value=\"Password test user\""));
        assertTrue(html.contains("value=\"password-test@example.com\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcde", ""})
    void existingMinimumAndRequiredRulesStillReject(String password) throws Exception {
        MvcResult result = submit(password, password);
        assertRejected(result);
        assertTrue(result.getResponse().getContentAsString().contains("Password must be at least 6 characters"));
    }

    @Test void confirmationMismatchBehaviourIsUnchanged() throws Exception {
        MvcResult result = submit("Secret123", "Different123");
        assertRejected(result);
        assertEquals("Passwords do not match", result.getModelAndView().getModel().get("errorMessage"));
    }

    private void assertRejected(MvcResult result) throws Exception {
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("auth/register", result.getModelAndView().getViewName());
        assertTrue(users.findByEmail("password-test@example.com").isEmpty());
        var form = (RegisterForm) result.getModelAndView().getModel().get("registerForm");
        assertNull(form.getPassword());
        assertNull(form.getConfirmPassword());
        String html = result.getResponse().getContentAsString();
        for (String field : new String[]{"password", "confirmPassword"}) {
            var input = Pattern.compile("<input[^>]*id=\"" + field + "\"[^>]*>").matcher(html);
            assertTrue(input.find());
            assertTrue(input.group().contains("value=\"\""), input.group());
        }
        assertFalse(html.contains("password cannot be more than 72 bytes"));
        assertFalse(html.contains("IllegalArgumentException"));
        assertFalse(html.contains("BCrypt"));
    }

    private MvcResult submit(String password, String confirmation) throws Exception {
        var session = new MockHttpSession();
        var page = mvc.perform(get("/register").session(session)).andExpect(status().isOk()).andReturn();
        var csrf = (CsrfToken) page.getRequest().getAttribute(CsrfToken.class.getName());
        return mvc.perform(post("/register").session(session).param(csrf.getParameterName(), csrf.getToken())
                .param("fullName", "Password test user").param("email", "password-test@example.com")
                .param("contactNumber", "0771234567").param("password", password)
                .param("confirmPassword", confirmation)).andReturn();
    }
}
