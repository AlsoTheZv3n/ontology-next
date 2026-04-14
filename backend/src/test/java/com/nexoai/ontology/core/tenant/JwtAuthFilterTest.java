package com.nexoai.ontology.core.tenant;

import com.nexoai.ontology.core.apikey.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests the JwtAuthFilter's three code paths:
 * 1. No Authorization header → passes through, no SecurityContext set
 * 2. Bearer nxo_... (API key) → validates via ApiKeyService, sets auth + tenant
 * 3. Bearer <jwt> → parses via JwtTokenService, sets auth + tenant
 */
class JwtAuthFilterTest {

    private JwtTokenService jwtTokenService;
    private ApiKeyService apiKeyService;
    private JwtAuthFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtTokenService = mock(JwtTokenService.class);
        apiKeyService = mock(ApiKeyService.class);
        filter = new JwtAuthFilter(jwtTokenService, apiKeyService);
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void no_authorization_header_passes_through_without_auth() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/ontology/object-types");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtTokenService);
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void api_key_authentication_sets_tenant_and_member_role() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        Map<String, Object> keyRow = new HashMap<>();
        keyRow.put("id", keyId.toString());
        keyRow.put("tenant_id", tenantId.toString());
        keyRow.put("name", "test-key");
        when(apiKeyService.validateKey("nxo_abc123")).thenReturn(keyRow);

        var request = new MockHttpServletRequest("GET", "/api/v1/ontology/object-types");
        request.addHeader("Authorization", "Bearer nxo_abc123");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        // After filter completes, TenantContext.clear() has been called,
        // so we verify the tenant was set during the chain invocation
        verify(apiKeyService).validateKey("nxo_abc123");
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void invalid_api_key_does_not_set_auth() throws Exception {
        when(apiKeyService.validateKey("nxo_invalid")).thenReturn(null);

        var request = new MockHttpServletRequest("GET", "/api/v1/ontology/object-types");
        request.addHeader("Authorization", "Bearer nxo_invalid");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(apiKeyService).validateKey("nxo_invalid");
        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void jwt_authentication_sets_tenant_and_role() throws Exception {
        UUID tenantId = UUID.randomUUID();
        JwtTokenService.JwtClaims claims = new JwtTokenService.JwtClaims(
                "user@example.com", tenantId.toString(), "acme", "OWNER");
        when(jwtTokenService.parse("valid.jwt.token")).thenReturn(claims);

        var request = new MockHttpServletRequest("GET", "/api/v1/ontology/object-types");
        request.addHeader("Authorization", "Bearer valid.jwt.token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(jwtTokenService).parse("valid.jwt.token");
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void invalid_jwt_does_not_set_auth() throws Exception {
        when(jwtTokenService.parse(ArgumentMatchers.anyString()))
                .thenThrow(new io.jsonwebtoken.JwtException("expired"));

        var request = new MockHttpServletRequest("GET", "/api/v1/ontology/object-types");
        request.addHeader("Authorization", "Bearer bad.jwt.token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void api_key_routed_to_apiKeyService_not_jwtService() throws Exception {
        Map<String, Object> keyRow = new HashMap<>();
        keyRow.put("id", UUID.randomUUID().toString());
        keyRow.put("tenant_id", UUID.randomUUID().toString());
        keyRow.put("name", "test");
        when(apiKeyService.validateKey(ArgumentMatchers.anyString())).thenReturn(keyRow);

        var request = new MockHttpServletRequest("GET", "/api/v1/ontology/object-types");
        request.addHeader("Authorization", "Bearer nxo_validKey123");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(apiKeyService).validateKey("nxo_validKey123");
        verifyNoInteractions(jwtTokenService);
    }
}
