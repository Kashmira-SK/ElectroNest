package lk.sliit.electronest.cart.controller;

import jakarta.servlet.Filter;
import lk.sliit.electronest.cart.dto.CartItemView;
import lk.sliit.electronest.cart.dto.DeliveryDetailsForm;
import lk.sliit.electronest.cart.service.CartService;
import lk.sliit.electronest.common.model.*;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.common.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class CheckoutPostalCodeTest {
    static final String MESSAGE = "Postal code must be 3 to 10 characters and contain only letters, numbers, spaces, or hyphens.";
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @MockitoBean CartService cart;
    MockMvc mvc;
    MockHttpSession session;
    User customer;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        customer = users.saveAndFlush(User.builder().fullName("Postal Customer").email("postal@example.com")
                .password("unused").role(Role.CUSTOMER).status(AccountStatus.ACTIVE).build());
        customer.setDeliveryPostalCode("10100");
        users.saveAndFlush(customer);
        var principal = new CustomUserDetails(customer);
        session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        when(cart.getCartItemViews(customer.getId())).thenReturn(List.of(
                new CartItemView(1L, 1L, "Keyboard", "ElectroNest", null, 1, BigDecimal.TEN, BigDecimal.TEN, 5)));
        when(cart.calculateSubtotal(customer.getId())).thenReturn(BigDecimal.TEN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"101", "10100", "SW1A 1AA", "12345-6789", "  AB-123  "})
    void optionalAndValidPostalCodesSaveTrimmed(String value) throws Exception {
        submit(value).andExpect(redirectedUrl("/checkout")).andExpect(flash().attributeExists("successMessage"));
        assertEquals(value == null || value.isEmpty() ? null : value.trim(),
                users.findById(customer.getId()).orElseThrow().getDeliveryPostalCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "   ", "12", "12345678901", "12@45", "12/45", "AB_123"})
    void invalidPostalCodePreservesDraftAndRendersInlineError(String value) throws Exception {
        var result = submit(value).andExpect(redirectedUrl("/checkout")).andReturn();
        var binding = (BindingResult) result.getFlashMap().get(BindingResult.MODEL_KEY_PREFIX + "deliveryDetailsForm");
        assertNotNull(binding);
        assertEquals(MESSAGE, binding.getFieldError("deliveryPostalCode").getDefaultMessage());
        assertEquals(value, ((DeliveryDetailsForm) result.getFlashMap().get("deliveryDetailsForm")).getDeliveryPostalCode());
        assertEquals("10100", users.findById(customer.getId()).orElseThrow().getDeliveryPostalCode());
        String html = mvc.perform(get("/checkout").session(session).flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("value=\"" + value + "\""));
        assertTrue(html.contains("value=\"Postal Customer\""));
        assertTrue(html.matches("(?s).*<small[^>]*id=\"deliveryPostalCodeError\"[^>]*>" + MESSAGE.replace(".", "\\.") + "</small>.*"));
        assertTrue(html.contains("aria-invalid=\"true\""));
    }

    @Test void initialFormProvidesClientErrorElementAndScript() throws Exception {
        String html = mvc.perform(get("/checkout").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("id=\"deliveryPostalCodeError\""));
        assertTrue(html.contains("/js/checkout-postal-code.js"));
    }

    ResultActions submit(String postalCode) throws Exception {
        var page = mvc.perform(get("/checkout").session(session)).andExpect(status().isOk()).andReturn();
        var csrf = (CsrfToken) page.getRequest().getAttribute(CsrfToken.class.getName());
        var request = post("/checkout/delivery/save").session(session)
                .param(csrf.getParameterName(), csrf.getToken())
                .param("deliveryName", "Postal Customer").param("deliveryPhone", "0771234567")
                .param("deliveryAddressLine1", "10 Main Street").param("deliveryCity", "Colombo")
                .param("deliveryCountry", "Sri Lanka");
        if (postalCode != null) request.param("deliveryPostalCode", postalCode);
        return mvc.perform(request);
    }
}
