package com.nexoai.ontology.adapters.in.rest.auth;

import com.nexoai.ontology.adapters.out.persistence.entity.TenantEntity;
import com.nexoai.ontology.adapters.out.persistence.entity.TenantUserEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaTenantRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaTenantUserRepository;
import com.nexoai.ontology.core.exception.OntologyException;
import com.nexoai.ontology.core.tenant.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JpaTenantRepository tenantRepository;
    private final JpaTenantUserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        TenantUserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new OntologyException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new OntologyException("Invalid credentials");
        }

        TenantEntity tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new OntologyException("Tenant not found"));

        String token = jwtTokenService.generateToken(
                user.getEmail(), tenant.getId(), tenant.getApiName(), user.getRole());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "email", user.getEmail(),
                "role", user.getRole(),
                "tenantId", tenant.getId().toString(),
                "tenantName", tenant.getDisplayName(),
                "tenantApiName", tenant.getApiName()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String tenantApiName = request.get("tenantApiName");
        String tenantDisplayName = request.get("tenantDisplayName");
        String email = request.get("email");
        String password = request.get("password");

        if (tenantRepository.existsByApiName(tenantApiName)) {
            throw new OntologyException("Tenant already exists: " + tenantApiName);
        }

        // Create tenant
        TenantEntity tenant = tenantRepository.save(TenantEntity.builder()
                .apiName(tenantApiName)
                .displayName(tenantDisplayName)
                .plan("FREE")
                .build());

        // Create owner user
        TenantUserEntity user = userRepository.save(TenantUserEntity.builder()
                .tenantId(tenant.getId())
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role("OWNER")
                .build());

        String token = jwtTokenService.generateToken(
                user.getEmail(), tenant.getId(), tenant.getApiName(), user.getRole());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "tenantId", tenant.getId().toString(),
                "tenantApiName", tenant.getApiName(),
                "message", "Tenant created successfully"
        ));
    }
}
