package com.nexoai.ontology.core.domain.object;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OntologyObject {
    private UUID id;
    private UUID objectTypeId;
    private String objectTypeName;
    private JsonNode properties;
    private Instant createdAt;
    private Instant updatedAt;
}
