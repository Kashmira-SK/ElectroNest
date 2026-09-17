package lk.sliit.electronest.common.config;

import lk.sliit.electronest.common.security.CustomAuthenticationSuccessHandler;
import lk.sliit.electronest.common.security.AccountStateFilter;
import lk.sliit.electronest.common.repository.UserRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity // enables @PreAuthorize("hasRole('ADMIN')") on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationSuccessHandler successHandler;
    private final UserRepository userRepository;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt - industry standard for hashing passwords, never store plain text
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .addFilterBefore(new AccountStateFilter(userRepository), CsrfFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Public pages - anyone can view/register/login
                .requestMatchers("/", "/login", "/register", "/products", "/products/**", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                // Everything under /admin/** requires the ADMIN role
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // TEMP: other modules' REST APIs are open while each module builds its own
                // auth/role checks. Tighten this per-endpoint as modules mature (pre-demo).
                .requestMatchers("/api/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")   // our login form field is "email", not the default "username"
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, denied) -> {
                if (request.getServletPath().startsWith("/api/")) {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"Access denied\"}");
                } else {
                    response.sendRedirect(request.getContextPath() + "/access-denied");
                }
            }));

        return http.build();
    }
}
