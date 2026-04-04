package com.nexoai.ontology.adapters.in.rest.dto;

import com.nexoai.ontology.core.domain.PropertyDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePropertyTypeRequest(
        @NotBlank @Size(max = 100)
        String apiName,
        @NotBlank @Size(max = 255)
        String displayName,
        @NotNull
        PropertyDataType dataType,
        boolean isPrimaryKey,
        boolean isRequired,
        boolean isIndexed,
        String defaultValue,
        String description
) {}
