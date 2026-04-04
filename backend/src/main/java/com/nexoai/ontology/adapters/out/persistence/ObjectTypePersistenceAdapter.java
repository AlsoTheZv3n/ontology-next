package com.nexoai.ontology.adapters.out.persistence;

import com.nexoai.ontology.adapters.out.persistence.mapper.ObjectTypeMapper;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectTypeRepository;
import com.nexoai.ontology.core.domain.ObjectType;
import com.nexoai.ontology.core.domain.ports.out.ObjectTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ObjectTypePersistenceAdapter implements ObjectTypeRepository {

    private final JpaObjectTypeRepository jpaRepository;
    private final ObjectTypeMapper mapper;

    @Override
    public ObjectType save(ObjectType objectType) {
        var entity = mapper.toEntity(objectType);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ObjectType> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<ObjectType> findAllActive() {
        return jpaRepository.findByIsActiveTrue().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByApiName(String apiName) {
        return jpaRepository.existsByApiName(apiName);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
