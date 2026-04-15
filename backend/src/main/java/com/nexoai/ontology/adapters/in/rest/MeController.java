package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.gdpr.GdprErasureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Self-service endpoints scoped to the currently-authenticated user.
 *
 * The GDPR self-service path (/me/erase) is the counterpart to the admin-only
 * /api/v1/audit/gdpr/erase/{email} shipped in prod-14. Art. 17 gives each
 * data subject a direct right to erasure — not only via an admin ticket.
 *
 * A confirmation phrase is required because browser history, accidental
 * double-clicks, and copy-pasted curl commands all make destructive
 * endpoints scarily easy to trigger by mistake. The phrase must match
 * exactly "DELETE MY DATA".
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    static final String REQUIRED_CONFIRMATION = "DELETE MY DATA";

    private final GdprErasureService erasureService;

    @GetMapping("/erase/dry-run")
    public GdprErasureService.EraseReport dryRun(
            @AuthenticationPrincipal UserDetails principal) {
        String email = resolveEmail(principal);
        return erasureService.dryRun(email);
    }

    @PostMapping("/erase")
    public ResponseEntity<?> eraseMe(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody(required = false) Map<String, String> body) {

        String confirm = body == null ? "" : body.getOrDefault("confirm", "");
        if (!REQUIRED_CONFIRMATION.equals(confirm)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "confirmation_phrase_required",
                    "message", "POST { \"confirm\": \"" + REQUIRED_CONFIRMATION + "\" }"
            ));
        }
        String email = resolveEmail(principal);
        var report = erasureService.eraseByEmail(email, email, "self-service");
        return ResponseEntity.ok(report);
    }

    private String resolveEmail(UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            throw new IllegalStateException("no authenticated principal");
        }
        return principal.getUsername();
    }
}
