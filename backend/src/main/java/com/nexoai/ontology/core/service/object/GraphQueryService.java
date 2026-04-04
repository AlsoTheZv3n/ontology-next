package com.nexoai.ontology.core.service.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.ObjectLinkEntity;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectLinkRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectTypeRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaLinkTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GraphQueryService {

    private final JpaOntologyObjectRepository objectRepository;
    private final JpaObjectLinkRepository linkRepository;
    private final JpaObjectTypeRepository objectTypeRepository;
    private final JpaLinkTypeRepository linkTypeRepository;
    private final ObjectMapper objectMapper;

    /**
     * Load an object graph via BFS starting from rootObjectId up to given depth.
     */
    public GraphData loadObjectGraph(UUID rootObjectId, int depth, List<String> linkTypeFilter) {
        Set<GraphNode> nodes = new LinkedHashSet<>();
        Set<GraphEdge> edges = new LinkedHashSet<>();

        Queue<UUID> queue = new LinkedList<>();
        Map<UUID, Integer> visited = new HashMap<>();
        queue.add(rootObjectId);
        visited.put(rootObjectId, 0);

        // Resolve link type names to IDs for filtering
        Map<UUID, String> linkTypeNames = new HashMap<>();
        linkTypeRepository.findAll().forEach(lt -> linkTypeNames.put(lt.getId(), lt.getApiName()));

        while (!queue.isEmpty()) {
            UUID currentId = queue.poll();
            int currentDepth = visited.get(currentId);

            OntologyObjectEntity entity = objectRepository.findById(currentId).orElse(null);
            if (entity == null) continue;

            String typeName = objectTypeRepository.findById(entity.getObjectTypeId())
                    .map(t -> t.getApiName()).orElse("unknown");
            JsonNode props = parseJson(entity.getProperties());
            String label = extractLabel(props, typeName);

            nodes.add(new GraphNode(currentId.toString(), typeName, label, props, currentDepth));

            if (currentDepth >= depth) continue;

            // Outgoing links
            List<ObjectLinkEntity> outgoing = linkRepository.findBySourceId(currentId);
            for (ObjectLinkEntity link : outgoing) {
                String linkTypeName = linkTypeNames.getOrDefault(link.getLinkTypeId(), "unknown");

                if (linkTypeFilter != null && !linkTypeFilter.isEmpty()
                        && !linkTypeFilter.contains(linkTypeName)) {
                    continue;
                }

                edges.add(new GraphEdge(
                        link.getId().toString(),
                        currentId.toString(),
                        link.getTargetId().toString(),
                        linkTypeName,
                        linkTypeName
                ));

                if (!visited.containsKey(link.getTargetId())) {
                    visited.put(link.getTargetId(), currentDepth + 1);
                    queue.add(link.getTargetId());
                }
            }

            // Incoming links (reverse direction)
            List<ObjectLinkEntity> incoming = linkRepository.findByTargetId(currentId);
            for (ObjectLinkEntity link : incoming) {
                String linkTypeName = linkTypeNames.getOrDefault(link.getLinkTypeId(), "unknown");

                if (linkTypeFilter != null && !linkTypeFilter.isEmpty()
                        && !linkTypeFilter.contains(linkTypeName)) {
                    continue;
                }

                edges.add(new GraphEdge(
                        link.getId().toString(),
                        link.getSourceId().toString(),
                        currentId.toString(),
                        linkTypeName,
                        linkTypeName
                ));

                if (!visited.containsKey(link.getSourceId())) {
                    visited.put(link.getSourceId(), currentDepth + 1);
                    queue.add(link.getSourceId());
                }
            }
        }

        log.info("Graph loaded: {} nodes, {} edges (root={}, depth={})", nodes.size(), edges.size(), rootObjectId, depth);
        return new GraphData(new ArrayList<>(nodes), new ArrayList<>(edges));
    }

    /**
     * Find shortest path between two objects via BFS.
     */
    public List<GraphNode> findPath(UUID fromId, UUID toId, int maxHops) {
        Queue<List<UUID>> queue = new LinkedList<>();
        Set<UUID> visited = new HashSet<>();

        queue.add(List.of(fromId));
        visited.add(fromId);

        while (!queue.isEmpty()) {
            List<UUID> path = queue.poll();
            UUID current = path.get(path.size() - 1);

            if (current.equals(toId)) {
                return path.stream().map(id -> {
                    var entity = objectRepository.findById(id).orElse(null);
                    if (entity == null) return null;
                    String typeName = objectTypeRepository.findById(entity.getObjectTypeId())
                            .map(t -> t.getApiName()).orElse("unknown");
                    JsonNode props = parseJson(entity.getProperties());
                    return new GraphNode(id.toString(), typeName, extractLabel(props, typeName), props, 0);
                }).filter(Objects::nonNull).toList();
            }

            if (path.size() > maxHops) continue;

            // Expand neighbors
            List<ObjectLinkEntity> links = new ArrayList<>();
            links.addAll(linkRepository.findBySourceId(current));
            links.addAll(linkRepository.findByTargetId(current));

            for (ObjectLinkEntity link : links) {
                UUID neighbor = link.getSourceId().equals(current) ? link.getTargetId() : link.getSourceId();
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    List<UUID> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(newPath);
                }
            }
        }

        return List.of(); // No path found
    }

    private String extractLabel(JsonNode props, String typeName) {
        // Try common label properties
        for (String key : List.of("name", "displayName", "title", "label", "id")) {
            if (props.has(key) && props.get(key).isTextual()) {
                return props.get(key).asText();
            }
        }
        return typeName;
    }

    private JsonNode parseJson(String json) {
        if (json == null) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    // DTOs
    public record GraphData(List<GraphNode> nodes, List<GraphEdge> edges) {}
    public record GraphNode(String id, String objectType, String label, JsonNode properties, int depth) {}
    public record GraphEdge(String id, String sourceId, String targetId, String linkType, String label) {}
}
