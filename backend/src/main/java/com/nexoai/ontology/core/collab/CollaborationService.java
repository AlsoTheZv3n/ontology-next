package com.nexoai.ontology.core.collab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollaborationService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w.+-]+@[\\w.-]+)");

    // --- Comments ---

    public Map<String, Object> addComment(UUID tenantId, UUID objectId, String author, String content) {
        UUID commentId = UUID.randomUUID();

        // Extract mentions from content
        List<String> mentions = new ArrayList<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }

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

            return jdbcTemplate.queryForMap(
                    "SELECT * FROM object_comments WHERE id = ?::uuid", commentId.toString());
        } catch (Exception e) {
            throw new OntologyException("Failed to add comment: " + e.getMessage());
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
