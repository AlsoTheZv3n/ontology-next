package com.nexoai.ontology.adapters.out.persistence.mapper;

import com.nexoai.ontology.adapters.out.persistence.entity.ObjectTypeEntity;
import com.nexoai.ontology.adapters.out.persistence.entity.PropertyTypeEntity;
import com.nexoai.ontology.core.domain.ObjectType;
import com.nexoai.ontology.core.domain.PropertyDataType;
import com.nexoai.ontology.core.domain.PropertyType;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ObjectTypeMapper {

    public ObjectType toDomain(ObjectTypeEntity entity) {
        return ObjectType.builder()
                .id(entity.getId())
                .apiName(entity.getApiName())
                .displayName(entity.getDisplayName())
                .description(entity.getDescription())
                .icon(entity.getIcon())
                .color(entity.getColor())
                .isActive(entity.isActive())
                .properties(entity.getProperties().stream()
                        .map(this::toPropertyDomain)
                        .collect(Collectors.toList()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ObjectTypeEntity toEntity(ObjectType domain) {
        ObjectTypeEntity entity = ObjectTypeEntity.builder()
                .id(domain.getId())
                .apiName(domain.getApiName())
                .displayName(domain.getDisplayName())
                .description(domain.getDescription())
                .icon(domain.getIcon())
                .color(domain.getColor())
                .isActive(domain.isActive())
                .build();

        if (domain.getProperties() != null) {
            domain.getProperties().forEach(prop -> {
                PropertyTypeEntity propEntity = toPropertyEntity(prop);
                propEntity.setObjectType(entity);
                entity.getProperties().add(propEntity);
            });
        }

        return entity;
    }

    public PropertyType toPropertyDomain(PropertyTypeEntity entity) {
        return PropertyType.builder()
                .id(entity.getId())
                .apiName(entity.getApiName())
                .displayName(entity.getDisplayName())
                .dataType(PropertyDataType.valueOf(entity.getDataType()))
                .isPrimaryKey(entity.isPrimaryKey())
                .isRequired(entity.isRequired())
                .isIndexed(entity.isIndexed())
                .defaultValue(entity.getDefaultValue())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public PropertyTypeEntity toPropertyEntity(PropertyType domain) {
        return PropertyTypeEntity.builder()
                .id(domain.getId())
                .apiName(domain.getApiName())
                .displayName(domain.getDisplayName())
                .dataType(domain.getDataType().name())
                .isPrimaryKey(domain.isPrimaryKey())
                .isRequired(domain.isRequired())
                .isIndexed(domain.isIndexed())
                .defaultValue(domain.getDefaultValue())
                .description(domain.getDescription())
                .build();
    }
}
