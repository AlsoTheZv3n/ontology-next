package com.nexoai.ontology.adapters.in.rest.dto;

import com.nexoai.ontology.core.domain.LinkType;

import java.time.Instant;
import java.util.UUID;

public record LinkTypeResponse(
        UUID id,
        String apiName,
        String displayName,
        UUID sourceObjectTypeId,
        UUID targetObjectTypeId,
        String cardinality,
        String description,
        Instant createdAt
) {
    public static LinkTypeResponse from(LinkType domain) {
        return new LinkTypeResponse(
                domain.getId(),
                domain.getApiName(),
                domain.getDisplayName(),
                domain.getSourceObjectTypeId(),
                domain.getTargetObjectTypeId(),
                domain.getCardinality().name(),
                domain.getDescription(),
                domain.getCreatedAt()
        );
    }
}
