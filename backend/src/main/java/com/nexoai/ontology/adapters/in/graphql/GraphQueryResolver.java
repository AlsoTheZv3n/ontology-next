package com.nexoai.ontology.adapters.in.graphql;

import com.nexoai.ontology.core.service.object.GraphQueryService;
import com.nexoai.ontology.core.service.object.GraphQueryService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.*;

@Controller
@RequiredArgsConstructor
public class GraphQueryResolver {

    private final GraphQueryService graphQueryService;

    @QueryMapping
    public Map<String, Object> loadObjectGraph(@Argument String rootObjectId,
                                                @Argument Integer depth,
                                                @Argument List<String> linkTypeFilter) {
        int d = depth != null ? depth : 2;
        List<String> filter = linkTypeFilter != null ? linkTypeFilter : List.of();

        GraphData data = graphQueryService.loadObjectGraph(UUID.fromString(rootObjectId), d, filter);

        List<Map<String, Object>> nodes = data.nodes().stream().map(n -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", n.id());
            map.put("objectType", n.objectType());
            map.put("label", n.label());
            map.put("properties", n.properties());
            map.put("depth", n.depth());
            return map;
        }).toList();

        List<Map<String, Object>> edges = data.edges().stream().map(e -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", e.id());
            map.put("sourceId", e.sourceId());
            map.put("targetId", e.targetId());
            map.put("linkType", e.linkType());
            map.put("label", e.label());
            return map;
        }).toList();

        return Map.of("nodes", nodes, "edges", edges);
    }

    @QueryMapping
    public List<Map<String, Object>> findPath(@Argument String fromObjectId,
                                               @Argument String toObjectId,
                                               @Argument Integer maxHops) {
        int hops = maxHops != null ? maxHops : 5;
        List<GraphNode> path = graphQueryService.findPath(
                UUID.fromString(fromObjectId), UUID.fromString(toObjectId), hops);

        if (path == null) return null;

        return path.stream().map(n -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", n.id());
            map.put("objectType", n.objectType());
            map.put("label", n.label());
            map.put("properties", n.properties());
            map.put("depth", n.depth());
            return map;
        }).toList();
    }
}
