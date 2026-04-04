package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.ObjectHistoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaObjectHistoryRepository extends JpaRepository<ObjectHistoryEntity, UUID> {

    List<ObjectHistoryEntity> findByObjectIdOrderByTxFromDesc(UUID objectId, Pageable pageable);

    @Query("SELECT h FROM ObjectHistoryEntity h WHERE h.objectId = :objectId " +
           "AND h.validFrom <= :asOf AND (h.validTo IS NULL OR h.validTo > :asOf) " +
           "ORDER BY h.txFrom DESC")
    Optional<ObjectHistoryEntity> findByObjectIdAndValidTime(
            @Param("objectId") UUID objectId, @Param("asOf") Instant asOf);

    @Modifying
    @Query("UPDATE ObjectHistoryEntity h SET h.txTo = :now, h.validTo = :now " +
           "WHERE h.objectId = :objectId AND h.txTo IS NULL")
    void closeCurrentSnapshot(@Param("objectId") UUID objectId, @Param("now") Instant now);
}
