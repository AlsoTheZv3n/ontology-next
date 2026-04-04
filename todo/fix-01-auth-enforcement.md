# Fix 01 — Auth Enforcement

**Priorität:** 🔴 Kritisch  
**Aufwand:** Medium  
**Status:** 🔲 Offen

---

## Problem

JWT + TenantInterceptor sind implementiert aber alle Endpoints stehen auf `permitAll()`. Jeder kann ohne Token auf jeden Endpoint zugreifen.

---

## Files

```
backend/src/main/java/com/nexoai/ontology/config/SecurityConfig.java
backend/src/main/java/com/nexoai/ontology/core/tenant/TenantInterceptor.java
backend/src/main/java/com/nexoai/ontology/core/tenant/JwtTokenService.java
```

---

## Fix: SecurityConfig.java

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenService jwtTokenService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // Public Endpoints
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/v1/inbound/n8n/**").permitAll()   // via Secret statt JWT
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/graphiql/**").permitAll()             // Nur in DEV

                // Alles andere: Auth required
                .requestMatchers("/graphql").authenticated()
                .requestMatchers("/api/v1/**").authenticated()
                .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/ws/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtTokenService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:3000",        // Dev
            "https://*.nexoai.ch",          // Production
            "https://nexus.besync.ch"       // BeSync Portal
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

---

## Fix: JwtAuthFilter.java (neu erstellen)

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            JwtClaims claims = jwtTokenService.parse(token);

            // TenantContext setzen
            TenantContext.setTenantId(UUID.fromString(claims.getTenantId()));

            // Spring Security Authentication setzen
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                claims.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + claims.getRole()))
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (JwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid or expired token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
```

---

## Fix: application.yml

```yaml
nexo:
  security:
    jwt-secret: ${JWT_SECRET:change-me-in-production-min-32-chars}
    jwt-expiry-hours: 24

  graphiql:
    enabled: ${GRAPHIQL_ENABLED:false}   # Nur in DEV auf true
```

---

## Fix: GraphQL Auth (für graphiql DEV-Only)

```java
@Configuration
@ConditionalOnProperty(name = "nexo.graphiql.enabled", havingValue = "true")
public class GraphQLDevConfig {

    @Bean
    public GraphiQlConfigurer graphiQlConfigurer() {
        return spec -> spec.path("/graphiql");
    }
}
```

---

## Fix: Role-based Method Security

```java
// Auf Service-Ebene für granulare Kontrolle
@Service
public class OntologyRegistryService {

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ObjectType registerObjectType(RegisterObjectTypeCommand cmd) { ... }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
    public List<ObjectType> getAllObjectTypes() { ... }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public void deleteObjectType(UUID id) { ... }
}
```

---

## Fix: Auth Endpoints

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        TenantUser user = userService.authenticate(request.email(), request.password());
        String token = jwtTokenService.generateToken(user);
        return ResponseEntity.ok(new LoginResponse(token, user.getTenantId(), user.getRole()));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody @Valid RegisterRequest request) {
        // Neuen Tenant + Owner erstellen
        Tenant tenant = tenantService.createTenant(request.tenantApiName(), request.displayName());
        TenantUser owner = userService.createOwner(tenant.getId(), request.email(), request.password());
        String token = jwtTokenService.generateToken(owner);
        return ResponseEntity.status(201).body(new RegisterResponse(token, tenant.getId()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestHeader("Authorization") String auth) {
        String oldToken = auth.substring(7);
        JwtClaims claims = jwtTokenService.parse(oldToken);
        TenantUser user = userService.findByEmail(claims.getEmail());
        String newToken = jwtTokenService.generateToken(user);
        return ResponseEntity.ok(new LoginResponse(newToken, user.getTenantId(), user.getRole()));
    }
}
```

---

## Akzeptanzkriterien

- [ ] `GET /api/v1/ontology/object-types` ohne Token → 401
- [ ] `GET /api/v1/ontology/object-types` mit gültigem JWT → 200
- [ ] `POST /api/auth/login` mit korrekten Credentials → JWT zurück
- [ ] `POST /api/admin/tenants` mit MEMBER-Token → 403
- [ ] `POST /api/v1/inbound/n8n/...` ohne JWT (aber mit Secret-Header) → funktioniert
- [ ] `/graphiql` nur erreichbar wenn `GRAPHIQL_ENABLED=true`
- [ ] TenantContext nach jedem Request gecleart (kein Tenant-Leak)

---

## .env.example Ergänzung

```env
JWT_SECRET=your-super-secret-key-minimum-32-characters
GRAPHIQL_ENABLED=true   # Nur DEV
```
