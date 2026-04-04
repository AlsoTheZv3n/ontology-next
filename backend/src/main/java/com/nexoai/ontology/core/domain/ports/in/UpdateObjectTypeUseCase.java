package com.nexoai.ontology.core.domain.ports.in;

import com.nexoai.ontology.core.domain.ObjectType;

import java.util.UUID;

public interface UpdateObjectTypeUseCase {

    ObjectType updateObjectType(UUID id, UpdateObjectTypeCommand command);

    void deactivateObjectType(UUID id);

    record UpdateObjectTypeCommand(
            String displayName,
            String description,
            String icon,
            String color
    ) {}
}
