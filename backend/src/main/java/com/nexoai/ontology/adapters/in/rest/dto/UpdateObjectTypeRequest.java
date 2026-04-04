package com.nexoai.ontology.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateObjectTypeRequest(
        @NotBlank @Size(max = 255)
        String displayName,
        String description,
        String icon,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Must be a valid hex color")
        String color
) {}
