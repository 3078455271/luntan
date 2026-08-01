package com.luntan.forum.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ForumDtos {

    private ForumDtos() {
    }

    public record SectionView(Long id, String slug, String name, String description, int sortOrder) {
    }

    public record AuthorView(Long id, String username, String displayName, String avatarUrl) {
    }

    public record PostSummary(
            Long id,
            SectionView section,
            AuthorView author,
            String title,
            long viewCount,
            long commentCount,
            long likeCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record PostView(
            Long id,
            SectionView section,
            AuthorView author,
            String title,
            String content,
            long viewCount,
            long commentCount,
            long likeCount,
            boolean likedByCurrentUser,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CommentView(
            Long id,
            Long postId,
            Long parentCommentId,
            AuthorView author,
            String content,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreatePostRequest(
            @NotNull Long sectionId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 100_000) String content) {
    }

    public record UpdatePostRequest(
            @NotNull Long sectionId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 100_000) String content) {
    }

    public record CreateCommentRequest(
            @NotBlank @Size(max = 10_000) String content,
            Long parentCommentId) {
    }

    public record LikeResponse(Long postId, boolean liked, long likeCount) {
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last) {
        public PageResponse {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }
}