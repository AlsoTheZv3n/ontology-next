package com.nexoai.ontology.core.domain.ports.out;

import com.nexoai.ontology.core.domain.ObjectType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObjectTypeRepository {
    ObjectType save(ObjectType objectType);
    Optional<ObjectType> findById(UUID id);
    List<ObjectType> findAllActive();
    boolean existsByApiName(String apiName);
    void deleteById(UUID id);
}
