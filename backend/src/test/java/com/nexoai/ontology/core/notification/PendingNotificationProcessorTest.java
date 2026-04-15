package com.nexoai.ontology.core.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PendingNotificationProcessorTest {

    private JdbcTemplate jdbc;
    private NotificationDispatcher dispatcher;
    private ObjectMapper mapper;
    private PendingNotificationProcessor processor;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        dispatcher = mock(NotificationDispatcher.class);
        mapper = new ObjectMapper();
        processor = new PendingNotificationProcessor(jdbc, dispatcher, mapper);
        ReflectionTestUtils.setField(processor, "enabled", true);
    }

    private Map<String, Object> row(UUID id, String email, String type, int attempts) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", id.toString());
        r.put("tenant_id", UUID.randomUUID().toString());
        r.put("user_email", email);
        r.put("type", type);
        r.put("title", "Test");
        r.put("message", "body");
        r.put("metadata", "{}");
        r.put("attempts", attempts);
        return r;
    }

    @Test
    void successful_dispatch_marks_delivered() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForList(contains("FROM notifications"), any(Object[].class)))
                .thenReturn(List.of(row(id, "alice@x.ai", "MENTION", 0)));
        when(jdbc.queryForList(contains("FROM notification_preferences"), anyString(), anyString()))
                .thenReturn(List.of(Map.of("channel", "SLACK", "event_types", "[]")));
        when(jdbc.queryForMap(contains("config FROM notification_preferences"),
                anyString(), anyString()))
                .thenReturn(Map.of("config", "{\"slack\":{\"webhookUrl\":\"x\"}}"));
        when(dispatcher.dispatch(any(), eq(List.of("SLACK")), any()))
                .thenReturn(List.of(new NotificationDispatcher.DeliveryOutcome("SLACK", true, null)));

        processor.drain();

        verify(jdbc).update(contains("UPDATE notifications"),
                eq("DELIVERED"), anyString(), eq("DELIVERED"), eq(id.toString()));
    }

    @Test
    void failed_dispatch_below_max_attempts_stays_pending() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForList(contains("FROM notifications"), any(Object[].class)))
                .thenReturn(List.of(row(id, "alice@x.ai", "MENTION", 0)));
        when(jdbc.queryForList(contains("FROM notification_preferences"), anyString(), anyString()))
                .thenReturn(List.of(Map.of("channel", "SLACK", "event_types", "[]")));
        when(jdbc.queryForMap(contains("config FROM notification_preferences"),
                anyString(), anyString()))
                .thenReturn(Map.of("config", "{}"));
        when(dispatcher.dispatch(any(), any(), any()))
                .thenReturn(List.of(new NotificationDispatcher.DeliveryOutcome("SLACK", false, "500")));

        processor.drain();

        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("UPDATE notifications"),
                status.capture(), anyString(), anyString(), eq(id.toString()));
        assertThat(status.getValue()).isEqualTo("PENDING");
    }

    @Test
    void failed_dispatch_at_max_attempts_marks_failed() {
        UUID id = UUID.randomUUID();
        // attempts = 2 → next (3rd) attempt reaches MAX_ATTEMPTS=3 and should go to FAILED
        when(jdbc.queryForList(contains("FROM notifications"), any(Object[].class)))
                .thenReturn(List.of(row(id, "alice@x.ai", "MENTION", 2)));
        when(jdbc.queryForList(contains("FROM notification_preferences"), anyString(), anyString()))
                .thenReturn(List.of(Map.of("channel", "SLACK", "event_types", "[]")));
        when(jdbc.queryForMap(contains("config FROM notification_preferences"),
                anyString(), anyString()))
                .thenReturn(Map.of("config", "{}"));
        when(dispatcher.dispatch(any(), any(), any()))
                .thenReturn(List.of(new NotificationDispatcher.DeliveryOutcome("SLACK", false, "500")));

        processor.drain();

        verify(jdbc).update(contains("UPDATE notifications"),
                eq("FAILED"), anyString(), anyString(), eq(id.toString()));
    }

    @Test
    void event_type_filter_is_respected() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForList(contains("FROM notifications"), any(Object[].class)))
                .thenReturn(List.of(row(id, "alice@x.ai", "MENTION", 0)));
        // SLACK only configured for WATCH_UPDATE, not MENTION → should be skipped
        when(jdbc.queryForList(contains("FROM notification_preferences"), anyString(), anyString()))
                .thenReturn(List.of(Map.of("channel", "SLACK",
                        "event_types", "[\"WATCH_UPDATE\"]")));
        when(jdbc.queryForMap(contains("config FROM notification_preferences"),
                anyString(), anyString()))
                .thenReturn(Map.of("config", "{}"));

        processor.drain();

        // No channels → goes straight to DELIVERED (in-app was the original write)
        verify(jdbc).update(contains("UPDATE notifications"),
                eq("DELIVERED"), anyString(), eq("DELIVERED"), eq(id.toString()));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void IN_APP_channel_is_excluded_from_fanout() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForList(contains("FROM notifications"), any(Object[].class)))
                .thenReturn(List.of(row(id, "alice@x.ai", "MENTION", 0)));
        when(jdbc.queryForList(contains("FROM notification_preferences"), anyString(), anyString()))
                .thenReturn(List.of(Map.of("channel", "IN_APP", "event_types", "[]")));
        when(jdbc.queryForMap(contains("config FROM notification_preferences"),
                anyString(), anyString()))
                .thenReturn(Map.of("config", "{}"));

        processor.drain();

        verifyNoInteractions(dispatcher);
    }

    @Test
    void empty_queue_does_nothing() {
        when(jdbc.queryForList(contains("FROM notifications"), any(Object[].class)))
                .thenReturn(List.of());

        processor.drain();

        verifyNoInteractions(dispatcher);
        verify(jdbc, never()).update(contains("UPDATE notifications"), any(Object[].class));
    }

    @Test
    void disabled_flag_skips_the_whole_drain() {
        ReflectionTestUtils.setField(processor, "enabled", false);

        processor.drain();

        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }
}
