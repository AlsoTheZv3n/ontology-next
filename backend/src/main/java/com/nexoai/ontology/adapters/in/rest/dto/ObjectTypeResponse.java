package com.nexoai.ontology.adapters.in.rest.dto;

import com.nexoai.ontology.core.domain.ObjectType;
import com.nexoai.ontology.core.domain.PropertyType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ObjectTypeResponse(
        UUID id,
        String apiName,
        String displayName,
        String description,
        String icon,
        String color,
        boolean isActive,
        List<PropertyTypeResponse> properties,
        Instant createdAt,
        Instant updatedAt
) {
    public static ObjectTypeResponse from(ObjectType domain) {
        return new ObjectTypeResponse(
                domain.getId(),
                domain.getApiName(),
                domain.getDisplayName(),
                domain.getDescription(),
                domain.getIcon(),
                domain.getColor(),
                domain.isActive(),
                domain.getProperties() != null
                        ? domain.getProperties().stream().map(PropertyTypeResponse::from).toList()
                        : List.of(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }

    public record PropertyTypeResponse(
            UUID id,
            String apiName,
            String displayName,
            String dataType,
            boolean isPrimaryKey,
            boolean isRequired,
            boolean isIndexed,
            String defaultValue,
            String description,
            Instant createdAt
    ) {
        public static PropertyTypeResponse from(PropertyType p) {
            return new PropertyTypeResponse(
                    p.getId(),
                    p.getApiName(),
                    p.getDisplayName(),
                    p.getDataType().name(),
                    p.isPrimaryKey(),
                    p.isRequired(),
                    p.isIndexed(),
                    p.getDefaultValue(),
                    p.getDescription(),
                    p.getCreatedAt()
            );
        }
    }
}
