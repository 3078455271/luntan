package com.luntan.forum.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<PostEntity, Long> {

    Optional<PostEntity> findByIdAndStatus(Long id, PostEntity.Status status);

    @Query(value = """
            SELECT p.*
            FROM posts p
            WHERE p.status = 'PUBLISHED'
              AND (:sectionId IS NULL OR p.section_id = :sectionId)
              AND (:authorId IS NULL OR p.author_id = :authorId)
              AND (:keyword IS NULL OR MATCH(p.title, p.content) AGAINST (:keyword IN NATURAL LANGUAGE MODE))
            ORDER BY p.created_at DESC, p.id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM posts p
            WHERE p.status = 'PUBLISHED'
              AND (:sectionId IS NULL OR p.section_id = :sectionId)
              AND (:authorId IS NULL OR p.author_id = :authorId)
              AND (:keyword IS NULL OR MATCH(p.title, p.content) AGAINST (:keyword IN NATURAL LANGUAGE MODE))
            """,
            nativeQuery = true)
    Page<PostEntity> search(@Param("sectionId") Long sectionId,
                            @Param("authorId") Long authorId,
                            @Param("keyword") String keyword,
                            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PostEntity p SET p.viewCount = p.viewCount + 1 WHERE p.id = :postId AND p.status = 'PUBLISHED'")
    int incrementViewCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PostEntity p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId AND p.status = 'PUBLISHED'")
    int incrementCommentCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PostEntity p SET p.commentCount = CASE WHEN p.commentCount > 0 THEN p.commentCount - 1 ELSE 0 END WHERE p.id = :postId")
    int decrementCommentCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PostEntity p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId AND p.status = 'PUBLISHED'")
    int incrementLikeCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PostEntity p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END WHERE p.id = :postId")
    int decrementLikeCount(@Param("postId") Long postId);
}