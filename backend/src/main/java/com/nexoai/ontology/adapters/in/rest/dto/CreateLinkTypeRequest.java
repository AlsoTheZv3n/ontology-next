package com.nexoai.ontology.adapters.in.rest.dto;

import com.nexoai.ontology.core.domain.Cardinality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateLinkTypeRequest(
        @NotBlank @Size(max = 100)
        String apiName,
        @NotBlank @Size(max = 255)
        String displayName,
        @NotNull
        UUID sourceObjectTypeId,
        @NotNull
        UUID targetObjectTypeId,
        Cardinality cardinality,
        String description
) {}
