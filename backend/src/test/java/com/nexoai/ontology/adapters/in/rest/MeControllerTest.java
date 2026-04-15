package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.gdpr.GdprErasureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeControllerTest {

    private GdprErasureService erasure;
    private MeController controller;
    private UserDetails alice;

    @BeforeEach
    void setUp() {
        erasure = mock(GdprErasureService.class);
        controller = new MeController(erasure);
        alice = User.withUsername("alice@x.ai").password("ignored").authorities("USER").build();
    }

    @Test
    void erase_without_confirmation_returns_400() {
        var resp = controller.eraseMe(alice, Map.of());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(erasure);
    }

    @Test
    void erase_with_wrong_phrase_returns_400() {
        var resp = controller.eraseMe(alice, Map.of("confirm", "yes please"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(erasure);
    }

    @Test
    void erase_with_null_body_returns_400_not_NPE() {
        var resp = controller.eraseMe(alice, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(erasure);
    }

    @Test
    void erase_with_correct_phrase_calls_service_with_self_as_initiator() {
        var report = new GdprErasureService.EraseReport(
                UUID.randomUUID(), 3, 5, List.of(), false);
        when(erasure.eraseByEmail(eq("alice@x.ai"), eq("alice@x.ai"), eq("self-service")))
                .thenReturn(report);

        var resp = controller.eraseMe(alice, Map.of("confirm", "DELETE MY DATA"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(report);
        verify(erasure).eraseByEmail("alice@x.ai", "alice@x.ai", "self-service");
    }

    @Test
    void dry_run_does_not_require_confirmation() {
        var report = new GdprErasureService.EraseReport(null, 0, 0, List.of(), true);
        when(erasure.dryRun("alice@x.ai")).thenReturn(report);

        var result = controller.dryRun(alice);

        assertThat(result).isEqualTo(report);
        verify(erasure).dryRun("alice@x.ai");
    }

    @Test
    void null_principal_is_rejected_loudly() {
        assertThatThrownBy(() -> controller.dryRun(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
