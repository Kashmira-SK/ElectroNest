package lk.sliit.electronest.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** Keep account status current and make suspended sessions read-only. */
@RequiredArgsConstructor
public class AccountStateFilter extends OncePerRequestFilter {
    private static final String SUSPENDED_MESSAGE =
            "This account is suspended. Transactional and account-changing actions are disabled.";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
            var current = userRepository.findById(details.getUser().getId()).orElse(null);
            if (current == null || current.getStatus() == AccountStatus.DEACTIVATED
                    || current.getRole() != details.getUser().getRole()) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                if (request.getServletPath().startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                } else {
                    response.sendRedirect(request.getContextPath() + "/login?accountChanged");
                }
                return;
            }

            CustomUserDetails refreshedDetails = new CustomUserDetails(current);
            var refreshedAuthentication = new UsernamePasswordAuthenticationToken(
                    refreshedDetails,
                    authentication.getCredentials(),
                    refreshedDetails.getAuthorities()
            );
            refreshedAuthentication.setDetails(authentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(refreshedAuthentication);

            if (current.getStatus() == AccountStatus.SUSPENDED
                    && isBlockedForSuspendedAccount(request)) {
                rejectSuspendedRequest(request, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isBlockedForSuspendedAccount(HttpServletRequest request) {
        String path = request.getServletPath();

        if ("/logout".equals(path)) {
            return false;
        }

        if (!SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }

        return startsWithAny(
                path,
                "/cart",
                "/checkout",
                "/payment",
                "/orders",
                "/vendor",
                "/admin",
                "/settings",
                "/api/cart",
                "/api/orders",
                "/api/payments",
                "/api/v1/payments",
                "/api/v1/receipts",
                "/api/products",
                "/api/reviews",
                "/api/vendors"
        );
    }

    private boolean startsWithAny(String path, String... prefixes) {
        for (String prefix : prefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private void rejectSuspendedRequest(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        if (request.getServletPath().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"" + SUSPENDED_MESSAGE + "\"}");
            return;
        }

        response.sendRedirect(
                request.getContextPath() + "/account/suspended?blocked"
        );
    }
}
