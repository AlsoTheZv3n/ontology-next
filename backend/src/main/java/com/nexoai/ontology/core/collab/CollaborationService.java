package com.nexoai.ontology.core.collab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.exception.OntologyException;
import com.nexoai.ontology.core.notification.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollaborationService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w.+-]+@[\\w.-]+)");

    /** Cap per-comment mentions so a single spammy message can't explode the notification queue. */
    static final int MAX_MENTIONS_PER_COMMENT = 10;

    // --- Comments ---

    public Map<String, Object> addComment(UUID tenantId, UUID objectId, String author, String content) {
        UUID commentId = UUID.randomUUID();

        List<String> mentions = extractMentions(content);

        try {
            String mentionsJson = objectMapper.writeValueAsString(mentions);

            jdbcTemplate.update(
                    """
                    INSERT INTO object_comments (id, tenant_id, object_id, author, content, mentions)
                    VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?::jsonb)
                    """,
                    commentId.toString(), tenantId.toString(), objectId.toString(),
                    author, content, mentionsJson);

            log.debug("Comment added to object {} by {}", objectId, author);

            notifyMentions(tenantId, objectId, author, content, commentId, mentions);
            notifyWatchers(tenantId, objectId, author, commentId);

            return jdbcTemplate.queryForMap(
                    "SELECT * FROM object_comments WHERE id = ?::uuid", commentId.toString());
        } catch (OntologyException e) {
            throw e;
        } catch (Exception e) {
            throw new OntologyException("Failed to add comment: " + e.getMessage());
        }
    }

    /**
     * Extract distinct @email mentions, case-normalized and capped so a single
     * comment can't generate an arbitrary number of notifications.
     */
    static List<String> extractMentions(String content) {
        if (content == null || content.isEmpty()) return List.of();
        Set<String> out = new LinkedHashSet<>();
        Matcher m = MENTION_PATTERN.matcher(content);
        while (m.find() && out.size() < MAX_MENTIONS_PER_COMMENT) {
            out.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(out);
    }

    private void notifyMentions(UUID tenantId, UUID objectId, String author,
                                 String content, UUID commentId, List<String> mentions) {
        String authorLower = author == null ? "" : author.toLowerCase(Locale.ROOT);
        for (String mentioned : mentions) {
            if (mentioned.equals(authorLower)) continue; // skip self-mention
            try {
                String metadata = objectMapper.writeValueAsString(Map.of(
                        "objectId", objectId.toString(),
                        "commentId", commentId.toString(),
                        "author", author == null ? "" : author
                ));
                String snippet = content.length() > 120 ? content.substring(0, 120) + "…" : content;
                notificationService.notify(tenantId, mentioned, "MENTION",
                        author + " mentioned you",
                        snippet, metadata);
                if (meterRegistry != null) {
                    meterRegistry.counter("nexo.notifications.mention.total").increment();
                }
            } catch (Exception e) {
                // Notification is non-critical — don't fail the comment if the
                // notification row can't be written.
                log.warn("Failed to deliver mention notification to {}: {}", mentioned, e.getMessage());
            }
        }
    }

    private void notifyWatchers(UUID tenantId, UUID objectId, String author, UUID commentId) {
        String authorLower = author == null ? "" : author.toLowerCase(Locale.ROOT);
        List<String> watchers;
        try {
            watchers = jdbcTemplate.queryForList(
                    "SELECT user_email FROM object_watches WHERE object_id = ?::uuid",
                    String.class, objectId.toString());
        } catch (Exception e) {
            log.debug("Could not load watchers for object {}: {}", objectId, e.getMessage());
            return;
        }
        for (String email : watchers) {
            if (email == null) continue;
            if (email.equalsIgnoreCase(authorLower)) continue;
            try {
                String metadata = objectMapper.writeValueAsString(Map.of(
                        "objectId", objectId.toString(),
                        "commentId", commentId.toString(),
                        "changeType", "COMMENT_ADDED"
                ));
                notificationService.notify(tenantId, email, "WATCH_UPDATE",
                        "New comment on an object you watch",
                        author + " added a comment", metadata);
                if (meterRegistry != null) {
                    meterRegistry.counter("nexo.notifications.watch.total").increment();
                }
            } catch (Exception e) {
                log.warn("Failed to deliver watch notification to {}: {}", email, e.getMessage());
            }
        }
    }

    public List<Map<String, Object>> getComments(UUID objectId) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM object_comments
                WHERE object_id = ?::uuid
                ORDER BY created_at DESC
                """,
                objectId.toString());
    }

    // --- Watches ---

    public Map<String, Object> watch(UUID tenantId, UUID objectId, String email) {
        // Toggle: if already watching, unwatch; otherwise watch
        int deleted = jdbcTemplate.update(
                "DELETE FROM object_watches WHERE object_id = ?::uuid AND user_email = ?",
                objectId.toString(), email);

        if (deleted > 0) {
            log.debug("User {} unwatched object {}", email, objectId);
            return Map.of("action", "unwatched", "objectId", objectId.toString(), "email", email);
        }

        UUID watchId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO object_watches (id, tenant_id, object_id, user_email)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?)
                """,
                watchId.toString(), tenantId.toString(), objectId.toString(), email);

        log.debug("User {} watching object {}", email, objectId);
        return Map.of("action", "watched", "objectId", objectId.toString(), "email", email);
    }

    public void unwatch(UUID objectId, String email) {
        jdbcTemplate.update(
                "DELETE FROM object_watches WHERE object_id = ?::uuid AND user_email = ?",
                objectId.toString(), email);
    }

    public List<Map<String, Object>> getWatchers(UUID objectId) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM object_watches
                WHERE object_id = ?::uuid
                ORDER BY created_at
                """,
                objectId.toString());
    }

    // --- Saved Searches ---

    public Map<String, Object> saveSearch(UUID tenantId, String email, String name, String query, boolean isShared) {
        UUID searchId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO saved_searches (id, tenant_id, user_email, name, query, is_shared)
                VALUES (?::uuid, ?::uuid, ?, ?, ?::jsonb, ?)
                """,
                searchId.toString(), tenantId.toString(), email, name,
                query != null ? query : "{}", isShared);

        log.debug("Saved search '{}' for user {} in tenant {}", name, email, tenantId);

        return jdbcTemplate.queryForMap(
                "SELECT * FROM saved_searches WHERE id = ?::uuid", searchId.toString());
    }

    public List<Map<String, Object>> getSavedSearches(UUID tenantId, String email) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM saved_searches
                WHERE tenant_id = ?::uuid AND (user_email = ? OR is_shared = TRUE)
                ORDER BY created_at DESC
                """,
                tenantId.toString(), email);
    }
}
