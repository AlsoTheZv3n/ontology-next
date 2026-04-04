package com.nexoai.ontology.adapters.out.persistence;

import com.nexoai.ontology.adapters.out.persistence.mapper.LinkTypeMapper;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaLinkTypeRepository;
import com.nexoai.ontology.core.domain.LinkType;
import com.nexoai.ontology.core.domain.ports.out.LinkTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LinkTypePersistenceAdapter implements LinkTypeRepository {

    private final JpaLinkTypeRepository jpaRepository;
    private final LinkTypeMapper mapper;

    @Override
    public LinkType save(LinkType linkType) {
        var entity = mapper.toEntity(linkType);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<LinkType> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<LinkType> findAll() {
        return jpaRepository.findAll().stream()
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
