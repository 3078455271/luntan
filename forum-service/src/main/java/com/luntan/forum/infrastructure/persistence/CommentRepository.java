package com.luntan.forum.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {

    Page<CommentEntity> findByPost_IdAndStatusOrderByCreatedAtAscIdAsc(
            Long postId,
            CommentEntity.Status status,
            Pageable pageable);

    Optional<CommentEntity> findByIdAndStatus(Long id, CommentEntity.Status status);
}