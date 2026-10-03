package com.nirmaan.reimburse.config;

import com.nirmaan.reimburse.user.PrincipalLoader;
import com.nirmaan.reimburse.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Form login today; authentication is isolated behind {@link PrincipalLoader} so an IITM SSO / OAuth2
 * login ({@code http.oauth2Login()}) can be added later by mapping the external email to a principal.
 * Authorisation is permission-based and enforced with {@code @PreAuthorize} on services.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptService loginAttempts,
                                            PrincipalLoader loader, UserRepository users,
                                            TransactionTemplate tx, Clock clock) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/invite/**", "/css/**", "/js/**", "/favicon.ico",
                                "/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(loginSuccess(loginAttempts))
                        .failureHandler(loginFailure(loginAttempts))
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
                .addFilterBefore(new LoginRateLimitFilter(loginAttempts), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new AuthorityRefreshFilter(loader, users, tx, clock), AnonymousAuthenticationFilter.class);
        // CSRF protection stays on (default). HTMX requests send the token via the X-CSRF-TOKEN header.
        return http.build();
    }

    private static AuthenticationSuccessHandler loginSuccess(LoginAttemptService attempts) {
        SimpleUrlAuthenticationSuccessHandler delegate = new SimpleUrlAuthenticationSuccessHandler("/");
        delegate.setAlwaysUseDefaultTargetUrl(true);
        return (request, response, authentication) -> {
            attempts.recordSuccess(authentication.getName());
            delegate.onAuthenticationSuccess(request, response, authentication);
        };
    }

    private static AuthenticationFailureHandler loginFailure(LoginAttemptService attempts) {
        SimpleUrlAuthenticationFailureHandler delegate = new SimpleUrlAuthenticationFailureHandler("/login?error");
        return (request, response, exception) -> {
            attempts.recordFailure(request.getParameter("username"));
            delegate.onAuthenticationFailure(request, response, exception);
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
