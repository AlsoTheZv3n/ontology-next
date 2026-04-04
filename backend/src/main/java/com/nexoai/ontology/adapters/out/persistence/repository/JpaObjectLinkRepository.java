package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.ObjectLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaObjectLinkRepository extends JpaRepository<ObjectLinkEntity, UUID> {
    List<ObjectLinkEntity> findBySourceIdAndLinkTypeId(UUID sourceId, UUID linkTypeId);
    List<ObjectLinkEntity> findBySourceId(UUID sourceId);
    List<ObjectLinkEntity> findByTargetId(UUID targetId);
}
