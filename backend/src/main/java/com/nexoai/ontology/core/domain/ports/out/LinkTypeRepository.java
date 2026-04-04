package com.nexoai.ontology.core.domain.ports.out;

import com.nexoai.ontology.core.domain.LinkType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkTypeRepository {
    LinkType save(LinkType linkType);
    Optional<LinkType> findById(UUID id);
    List<LinkType> findAll();
    boolean existsByApiName(String apiName);
    void deleteById(UUID id);
}
