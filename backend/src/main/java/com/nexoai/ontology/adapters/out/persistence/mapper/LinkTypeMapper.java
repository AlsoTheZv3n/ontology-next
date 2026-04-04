package com.nexoai.ontology.adapters.out.persistence.mapper;

import com.nexoai.ontology.adapters.out.persistence.entity.LinkTypeEntity;
import com.nexoai.ontology.core.domain.Cardinality;
import com.nexoai.ontology.core.domain.LinkType;
import org.springframework.stereotype.Component;

@Component
public class LinkTypeMapper {

    public LinkType toDomain(LinkTypeEntity entity) {
        return LinkType.builder()
                .id(entity.getId())
                .apiName(entity.getApiName())
                .displayName(entity.getDisplayName())
                .sourceObjectTypeId(entity.getSourceObjectTypeId())
                .targetObjectTypeId(entity.getTargetObjectTypeId())
                .cardinality(Cardinality.valueOf(entity.getCardinality()))
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public LinkTypeEntity toEntity(LinkType domain) {
        return LinkTypeEntity.builder()
                .id(domain.getId())
                .apiName(domain.getApiName())
                .displayName(domain.getDisplayName())
                .sourceObjectTypeId(domain.getSourceObjectTypeId())
                .targetObjectTypeId(domain.getTargetObjectTypeId())
                .cardinality(domain.getCardinality().name())
                .description(domain.getDescription())
                .build();
    }
}
