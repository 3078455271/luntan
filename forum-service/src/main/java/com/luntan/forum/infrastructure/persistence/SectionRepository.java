package com.luntan.forum.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<SectionEntity, Long> {

    List<SectionEntity> findAllByStatusOrderBySortOrderAscIdAsc(SectionEntity.Status status);

    Optional<SectionEntity> findByIdAndStatus(Long id, SectionEntity.Status status);
}