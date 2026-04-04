package com.nexoai.ontology.core.domain;

import com.nexoai.ontology.core.exception.OntologyException;
import lombok.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectType {
    private UUID id;
    private String apiName;
    private String displayName;
    private String description;
    private String icon;
    private String color;
    @Builder.Default
    private boolean isActive = true;
    @Builder.Default
    private List<PropertyType> properties = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public void addProperty(PropertyType property) {
        if (properties == null) {
            properties = new ArrayList<>();
        }
        if (hasPrimaryKey() && property.isPrimaryKey()) {
            throw new OntologyException("ObjectType already has a primary key");
        }
        if (properties.stream().anyMatch(p -> p.getApiName().equals(property.getApiName()))) {
            throw new OntologyException("Property '" + property.getApiName() + "' already exists");
        }
        this.properties.add(property);
    }

    private boolean hasPrimaryKey() {
        return properties != null && properties.stream().anyMatch(PropertyType::isPrimaryKey);
    }
}
