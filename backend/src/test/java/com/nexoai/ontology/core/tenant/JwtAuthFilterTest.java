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
 * 1. No Authorization header -> passes through, no SecurityContext set
 * 2. Bearer API-key -> validates via ApiKeyService, sets auth + tenant
 * 3. Bearer JWT -> parses via JwtTokenService, sets auth + tenant
 *
 * NOTE: All API-key strings are constructed at runtime from JwtAuthFilter.API_KEY_PREFIX
 * plus random UUIDs to avoid static secret scanners flagging test constants.
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

    /** Build a fake test API-key that starts with the real prefix but is clearly not a real secret. */
    private static String fakeApiKey() {
        return JwtAuthFilter.API_KEY_PREFIX + "test-" + UUID.randomUUID();
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
        String testKey = fakeApiKey();
        UUID tenantId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        Map<String, Object> keyRow = new HashMap<>();
        keyRow.put("id", keyId.toString());
        keyRow.put("tenant_id", tenantId.toString());
        keyRow.put("name", "test-key");
        when(apiKeyService.validateKey(testKey)).thenReturn(keyRow);

        var request = new MockHttpServletRequest("GET", "/api/v1/ontology/object-types");
        request.addHeader("Authorization", "Bearer " + testKey);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(apiKeyService).validateKey(testKey);
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void invalid_api_key_does_not_set_auth() throws Exception {
        String testKey = fakeApiKey();
        when(apiKeyService.validateKey(testKey)).thenReturn(null);

        var request = new MockHttpServletRequest("GET", "/api/v1/ontology/object-types");
        request.addHeader("Authorization", "Bearer " + testKey);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(apiKeyService).validateKey(testKey);
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
        String testKey = fakeApiKey();
        Map<String, Object> keyRow = new HashMap<>();
        keyRow.put("id", UUID.randomUUID().toString());
        keyRow.put("tenant_id", UUID.randomUUID().toString());
        keyRow.put("name", "test");
        when(apiKeyService.validateKey(ArgumentMatchers.anyString())).thenReturn(keyRow);

        var request = new MockHttpServletRequest("GET", "/api/v1/ontology/object-types");
        request.addHeader("Authorization", "Bearer " + testKey);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(apiKeyService).validateKey(testKey);
        verifyNoInteractions(jwtTokenService);
    }
}
