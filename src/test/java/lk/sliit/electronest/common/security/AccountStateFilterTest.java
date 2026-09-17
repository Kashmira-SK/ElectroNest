package lk.sliit.electronest.common.security;

import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.FilterChain;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountStateFilterTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final AccountStateFilter filter = new AccountStateFilter(repository);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void suspendedAccountCanBrowsePublicPagesAndKeepsSession() throws Exception {
        authenticate();
        User current = user(Role.CUSTOMER, AccountStatus.SUSPENDED);
        when(repository.findById(2L)).thenReturn(Optional.of(current));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setServletPath("/products/1");
        request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertNull(response.getRedirectedUrl());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        CustomUserDetails refreshed = (CustomUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        assertEquals(AccountStatus.SUSPENDED, refreshed.getUser().getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void suspendedAccountPostIsRedirectedBeforeBusinessAction() throws Exception {
        authenticate();
        when(repository.findById(2L)).thenReturn(Optional.of(
                user(Role.CUSTOMER, AccountStatus.SUSPENDED)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath("/cart/add");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals("/account/suspended?blocked", response.getRedirectedUrl());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain);
    }

    @Test
    void suspendedVendorSellerMutationIsRedirectedBeforeBusinessAction() throws Exception {
        authenticate(Role.VENDOR, AccountStatus.SUSPENDED);
        when(repository.findById(2L)).thenReturn(Optional.of(
                user(Role.VENDOR, AccountStatus.SUSPENDED)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath("/vendor/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals("/account/suspended?blocked", response.getRedirectedUrl());
        verifyNoInteractions(chain);
    }

    @Test
    void suspendedAccountBusinessAreaGetUsesStatusPage() throws Exception {
        authenticate();
        when(repository.findById(2L)).thenReturn(Optional.of(
                user(Role.CUSTOMER, AccountStatus.SUSPENDED)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setServletPath("/checkout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals("/account/suspended?blocked", response.getRedirectedUrl());
        verifyNoInteractions(chain);
    }

    @Test
    void suspendedAccountApiMutationGetsStructuredForbiddenResponse() throws Exception {
        authenticate();
        when(repository.findById(2L)).thenReturn(Optional.of(
                user(Role.CUSTOMER, AccountStatus.SUSPENDED)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath("/api/cart/items");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("account is suspended"));
        verifyNoInteractions(chain);
    }

    @Test
    void deactivationEndsExistingSession() throws Exception {
        authenticate();
        when(repository.findById(2L)).thenReturn(Optional.of(
                user(Role.CUSTOMER, AccountStatus.DEACTIVATED)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals("/login?accountChanged", response.getRedirectedUrl());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getSession(false));
        verifyNoInteractions(chain);
    }

    @Test
    void changedRoleRequiresFreshAuthentication() throws Exception {
        authenticate();
        when(repository.findById(2L)).thenReturn(Optional.of(user(Role.ADMIN, AccountStatus.ACTIVE)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void unchangedActiveAccountContinues() throws Exception {
        authenticate();
        when(repository.findById(2L)).thenReturn(Optional.of(user(Role.CUSTOMER, AccountStatus.ACTIVE)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void reactivationRestoresMutatingRequestsWithoutRelogin() throws Exception {
        authenticate(AccountStatus.SUSPENDED);
        when(repository.findById(2L)).thenReturn(Optional.of(
                user(Role.CUSTOMER, AccountStatus.ACTIVE)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath("/cart/add");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        CustomUserDetails refreshed = (CustomUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        assertEquals(AccountStatus.ACTIVE, refreshed.getUser().getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void suspendedCredentialsCanAuthenticateButDeactivatedCredentialsCannot() {
        CustomUserDetails suspended = new CustomUserDetails(
                user(Role.CUSTOMER, AccountStatus.SUSPENDED));
        CustomUserDetails deactivated = new CustomUserDetails(
                user(Role.CUSTOMER, AccountStatus.DEACTIVATED));

        assertTrue(suspended.isEnabled());
        assertTrue(suspended.isAccountNonLocked());
        assertFalse(deactivated.isEnabled());
    }

    @Test
    void publicBrowsingDoesNotRequireAccount() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(repository);
    }

    private void authenticate() {
        authenticate(AccountStatus.ACTIVE);
    }

    private void authenticate(AccountStatus status) {
        authenticate(Role.CUSTOMER, status);
    }

    private void authenticate(Role role, AccountStatus status) {
        var details = new CustomUserDetails(user(role, status));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private User user(Role role, AccountStatus status) {
        User user = new User();
        user.setId(2L);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
