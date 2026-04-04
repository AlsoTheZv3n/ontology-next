package com.nexoai.ontology.adapters.in.graphql;

import com.nexoai.ontology.core.ml.PropertyExtractionService;
import com.nexoai.ontology.core.ml.SemanticSearchService;
import com.nexoai.ontology.core.ml.SemanticSearchService.SemanticSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.*;

@Controller
@RequiredArgsConstructor
public class MLQueryResolver {

    private final SemanticSearchService semanticSearchService;
    private final PropertyExtractionService propertyExtractionService;

    @QueryMapping
    public List<Map<String, Object>> semanticSearch(
            @Argument String query,
            @Argument String objectType,
            @Argument Integer limit,
            @Argument Float minSimilarity) {

        int lim = limit != null ? limit : 10;
        float minSim = minSimilarity != null ? minSimilarity : 0.3f;

        List<SemanticSearchResult> results = semanticSearchService.search(query, objectType, lim, minSim);

        return results.stream().map(r -> {
            Map<String, Object> objectMap = new HashMap<>();
            objectMap.put("id", r.id().toString());
            objectMap.put("objectType", r.objectType());
            objectMap.put("properties", r.properties());
            objectMap.put("createdAt", r.createdAt());
            objectMap.put("updatedAt", null);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("object", objectMap);
            resultMap.put("similarity", r.similarity());
            return resultMap;
        }).toList();
    }

    @QueryMapping
    public Map<String, Object> extractProperties(@Argument String rawText, @Argument String targetObjectType) {
        return propertyExtractionService.extractProperties(rawText, targetObjectType);
    }
}
