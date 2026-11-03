package com.nirmaan.reimburse.mcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security for the machine-facing endpoints used by AI assistants: the MCP endpoint and the OAuth discovery,
 * registration, token and revocation endpoints. Stateless (no session cookies), so CSRF does not apply;
 * /mcp is protected by {@link McpBearerFilter}. The browser part of OAuth (/oauth/authorize) stays in the main
 * chain with normal login and CSRF.
 */
@Configuration
public class McpSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, OAuthService oauth) throws Exception {
        http
                .securityMatcher("/mcp", "/mcp/**", "/oauth/token", "/oauth/register", "/oauth/revoke", "/.well-known/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(rc -> rc.disable())
                .cors(cors -> cors.configurationSource(corsConfig()))
                .addFilterBefore(new McpBearerFilter(oauth), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /** Browser-based MCP tools (e.g. the MCP Inspector) may call these endpoints; tokens travel in headers, not cookies. */
    private static UrlBasedCorsConfigurationSource corsConfig() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of("*"));
        c.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Mcp-Session-Id", "Mcp-Protocol-Version"));
        c.setExposedHeaders(List.of("WWW-Authenticate", "Mcp-Session-Id"));
        c.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
