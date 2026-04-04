package com.nexoai.ontology.core.domain;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyType {
    private UUID id;
    private String apiName;
    private String displayName;
    private PropertyDataType dataType;
    private boolean isPrimaryKey;
    private boolean isRequired;
    private boolean isIndexed;
    private String defaultValue;
    private String description;
    private Instant createdAt;
}
