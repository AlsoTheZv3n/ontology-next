package com.nexoai.ontology.core.domain.ports.in;

import com.nexoai.ontology.core.domain.ObjectType;

import java.util.List;
import java.util.UUID;

public interface QueryObjectTypeUseCase {
    ObjectType getObjectType(UUID id);
    List<ObjectType> getAllObjectTypes();
}
