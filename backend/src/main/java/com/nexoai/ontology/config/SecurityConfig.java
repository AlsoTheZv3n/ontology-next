package com.nexoai.ontology.config;

import com.nexoai.ontology.core.tenant.JwtAuthFilter;
import com.nexoai.ontology.core.tenant.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final JwtAuthFilter jwtAuthFilter;

    @Value("${nexo.security.auth-enforced:false}")
    private boolean authEnforced;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        if (authEnforced) {
            http.authorizeHttpRequests(auth -> auth
                // Public — no auth required
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/v1/inbound/n8n/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Admin — only OWNER role (tenant creation, global admin ops)
                .requestMatchers("/api/admin/**").hasRole("OWNER")
                // DELETE writes require OWNER or ADMIN
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/**")
                        .hasAnyRole("OWNER", "ADMIN")
                // POST/PUT writes require OWNER, ADMIN or MEMBER
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/**")
                        .hasAnyRole("OWNER", "ADMIN", "MEMBER")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/**")
                        .hasAnyRole("OWNER", "ADMIN", "MEMBER")
                // Reads + GraphQL + WebSocket require authentication
                .requestMatchers("/graphql").authenticated()
                .requestMatchers("/graphiql/**").authenticated()
                .requestMatchers("/ws/**").authenticated()
                .requestMatchers("/api/v1/**").authenticated()
                .requestMatchers("/actuator/**").hasRole("OWNER")
                .anyRequest().authenticated()
            );
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "https://*.nexoai.ch",
                "https://nexus.besync.ch"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**", "/graphql");
    }
}
