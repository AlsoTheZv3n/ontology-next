package com.nexoai.ontology.core.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RecordMapper {

    private final ObjectMapper objectMapper;

    public OntologyObject toOntologyObject(RawRecord record, Map<String, String> columnMapping, UUID objectTypeId) {
        ObjectNode properties = objectMapper.createObjectNode();

        for (Map.Entry<String, Object> field : record.fields().entrySet()) {
            String sourceCol = field.getKey();
            String targetProp = columnMapping.getOrDefault(sourceCol, sourceCol);
            Object value = field.getValue();

            if (value == null) {
                properties.putNull(targetProp);
            } else if (value instanceof Timestamp ts) {
                properties.put(targetProp, ts.toInstant().toString());
            } else if (value instanceof BigDecimal bd) {
                properties.put(targetProp, bd.doubleValue());
            } else if (value instanceof Number num) {
                properties.put(targetProp, num.doubleValue());
            } else if (value instanceof Boolean bool) {
                properties.put(targetProp, bool);
            } else {
                properties.put(targetProp, value.toString());
            }
        }

        return OntologyObject.builder()
                .objectTypeId(objectTypeId)
                .properties(properties)
                .build();
    }
}
