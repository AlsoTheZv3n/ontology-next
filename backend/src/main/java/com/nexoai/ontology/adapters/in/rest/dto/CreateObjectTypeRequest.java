package com.nexoai.ontology.adapters.in.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateObjectTypeRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$")
        String apiName,
        @NotBlank @Size(max = 255)
        String displayName,
        String description,
        String icon,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Must be a valid hex color")
        String color,
        @Valid
        List<CreatePropertyTypeRequest> properties
) {}
