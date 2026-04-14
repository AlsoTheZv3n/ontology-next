package com.nexoai.ontology.core.collab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.notification.NotificationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollaborationServiceTest {

    private JdbcTemplate jdbc;
    private NotificationService notifications;
    private CollaborationService service;
    private UUID tenantId;
    private UUID objectId;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        notifications = mock(NotificationService.class);
        service = new CollaborationService(jdbc, new ObjectMapper(), notifications,
                new SimpleMeterRegistry());
        tenantId = UUID.randomUUID();
        objectId = UUID.randomUUID();
        when(jdbc.queryForMap(anyString(), anyString())).thenReturn(new HashMap<>());
        when(jdbc.queryForList(anyString(), eq(String.class), anyString()))
                .thenReturn(List.of());
    }

    @Test
    void comment_with_mention_triggers_MENTION_notification() {
        service.addComment(tenantId, objectId, "author@nexo.ai",
                "Hey @admin@nexo.ai check this out");

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        verify(notifications).notify(eq(tenantId), recipient.capture(), eventType.capture(),
                anyString(), anyString(), anyString());
        assertThat(recipient.getValue()).isEqualTo("admin@nexo.ai");
        assertThat(eventType.getValue()).isEqualTo("MENTION");
    }

    @Test
    void self_mention_does_not_trigger_notification() {
        service.addComment(tenantId, objectId, "author@nexo.ai",
                "Note to self @author@nexo.ai");

        verify(notifications, never()).notify(any(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void duplicate_mentions_deduplicate() {
        service.addComment(tenantId, objectId, "a@x.ai",
                "@bob@x.ai @bob@x.ai and again @bob@x.ai");

        verify(notifications, times(1)).notify(eq(tenantId), eq("bob@x.ai"),
                eq("MENTION"), anyString(), anyString(), anyString());
    }

    @Test
    void more_than_ten_mentions_are_capped() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 20; i++) s.append("@user").append(i).append("@x.ai ");

        service.addComment(tenantId, objectId, "a@x.ai", s.toString());

        verify(notifications, times(CollaborationService.MAX_MENTIONS_PER_COMMENT))
                .notify(any(), anyString(), eq("MENTION"), anyString(), anyString(), anyString());
    }

    @Test
    void watchers_except_author_receive_WATCH_UPDATE_notification() {
        when(jdbc.queryForList(contains("FROM object_watches"), eq(String.class), eq(objectId.toString())))
                .thenReturn(List.of("watcher1@x.ai", "author@nexo.ai", "watcher2@x.ai"));

        service.addComment(tenantId, objectId, "author@nexo.ai", "plain comment");

        // author excluded, two watchers notified
        verify(notifications).notify(eq(tenantId), eq("watcher1@x.ai"),
                eq("WATCH_UPDATE"), anyString(), anyString(), anyString());
        verify(notifications).notify(eq(tenantId), eq("watcher2@x.ai"),
                eq("WATCH_UPDATE"), anyString(), anyString(), anyString());
        verify(notifications, never()).notify(any(), eq("author@nexo.ai"),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void notification_failure_does_not_break_comment() {
        doThrow(new RuntimeException("notify down"))
                .when(notifications).notify(any(), anyString(), anyString(),
                        anyString(), anyString(), anyString());

        // addComment must still succeed; verify the INSERT was issued.
        Map<String, Object> result = service.addComment(tenantId, objectId, "a@x.ai",
                "Hi @b@x.ai");

        verify(jdbc).update(contains("INSERT INTO object_comments"),
                any(Object[].class));
        assertThat(result).isNotNull();
    }

    @Test
    void extractMentions_is_case_insensitive_and_distinct() {
        var m = CollaborationService.extractMentions("@Alice@x.ai @alice@x.ai @BOB@x.ai");
        assertThat(m).containsExactly("alice@x.ai", "bob@x.ai");
    }

    @Test
    void comment_without_mentions_or_watchers_makes_no_notification_calls() {
        service.addComment(tenantId, objectId, "a@x.ai", "plain text, no mentions");

        verify(notifications, never()).notify(any(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }
}
