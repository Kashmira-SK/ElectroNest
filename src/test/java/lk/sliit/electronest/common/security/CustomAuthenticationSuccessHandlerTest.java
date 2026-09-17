package lk.sliit.electronest.common.security;

import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomAuthenticationSuccessHandlerTest {

    private final CustomAuthenticationSuccessHandler handler =
            new CustomAuthenticationSuccessHandler();

    @Test
    void suspendedUserLogsInToDedicatedStatusPage() throws Exception {
        assertRedirect(Role.CUSTOMER, AccountStatus.SUSPENDED, "/account/suspended");
    }

    @Test
    void activeUsersKeepRoleBasedLandingPages() throws Exception {
        assertRedirect(Role.CUSTOMER, AccountStatus.ACTIVE, "/");
        assertRedirect(Role.VENDOR, AccountStatus.ACTIVE, "/");
        assertRedirect(Role.ADMIN, AccountStatus.ACTIVE, "/admin/dashboard");
    }

    private void assertRedirect(Role role, AccountStatus status, String expected)
            throws Exception {
        User user = new User();
        user.setRole(role);
        user.setStatus(status);
        CustomUserDetails details = new CustomUserDetails(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                details,
                null,
                details.getAuthorities()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals(expected, response.getRedirectedUrl());
    }
}
