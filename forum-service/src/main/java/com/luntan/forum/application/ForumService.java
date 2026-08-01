package com.luntan.forum.application;

import com.luntan.forum.api.dto.ForumDtos;
import com.luntan.forum.domain.ApiException;
import com.luntan.forum.infrastructure.client.UserSummaryClient;
import com.luntan.forum.infrastructure.persistence.CommentEntity;
import com.luntan.forum.infrastructure.persistence.CommentRepository;
import com.luntan.forum.infrastructure.persistence.PostEntity;
import com.luntan.forum.infrastructure.persistence.PostLikeId;
import com.luntan.forum.infrastructure.persistence.PostLikeRepository;
import com.luntan.forum.infrastructure.persistence.PostRepository;
import com.luntan.forum.infrastructure.persistence.SectionEntity;
import com.luntan.forum.infrastructure.persistence.SectionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForumService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SectionRepository sectionRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserSummaryClient userSummaryClient;

    public ForumService(SectionRepository sectionRepository,
                        PostRepository postRepository,
                        CommentRepository commentRepository,
                        PostLikeRepository postLikeRepository,
                        UserSummaryClient userSummaryClient) {
        this.sectionRepository = sectionRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.userSummaryClient = userSummaryClient;
    }

    @Transactional(readOnly = true)
    public List<ForumDtos.SectionView> listSections() {
        return sectionRepository.findAllByStatusOrderBySortOrderAscIdAsc(SectionEntity.Status.ACTIVE)
                .stream()
                .map(ForumService::toSectionView)
                .toList();
    }

    @Transactional
    public ForumDtos.PostView createPost(Long userId, ForumDtos.CreatePostRequest request) {
        requireUser(userId);
        SectionEntity section = requireActiveSection(request.sectionId());
        PostEntity post = postRepository.save(new PostEntity(
                section,
                userId,
                request.title().trim(),
                request.content().trim()));
        return toPostView(post, userId, userSummaryClient.findAll(Set.of(userId)));
    }

    @Transactional
    public ForumDtos.PostView updatePost(Long userId, Long postId, ForumDtos.UpdatePostRequest request) {
        requireUser(userId);
        PostEntity post = requireActivePost(postId);
        requireOwner(userId, post.getAuthorId());
        SectionEntity section = requireActiveSection(request.sectionId());
        post.update(section, request.title().trim(), request.content().trim());
        return toPostView(post, userId, userSummaryClient.findAll(Set.of(userId)));
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        requireUser(userId);
        PostEntity post = requireActivePost(postId);
        requireOwner(userId, post.getAuthorId());
        post.delete(Instant.now());
    }

    @Transactional
    public ForumDtos.PostView getPost(Long postId, Long currentUserId) {
        if (postRepository.incrementViewCount(postId) == 0) {
            throw postNotFound();
        }
        PostEntity post = requireActivePost(postId);
        Set<Long> userIds = currentUserId == null
                ? Set.of(post.getAuthorId())
                : Set.of(post.getAuthorId(), currentUserId);
        boolean liked = currentUserId != null
                && postLikeRepository.existsById(new PostLikeId(postId, currentUserId));
        return toPostView(post, currentUserId, userSummaryClient.findAll(userIds), liked);
    }

    @Transactional(readOnly = true)
    public ForumDtos.PageResponse<ForumDtos.PostSummary> searchPosts(Long sectionId,
                                                                      Long authorId,
                                                                      String keyword,
                                                                      int page,
                                                                      int size) {
        Pageable pageable = pageRequest(page, size);
        Page<PostEntity> posts = postRepository.search(
                sectionId,
                authorId,
                normalizeKeyword(keyword),
                pageable);
        Map<Long, UserSummaryClient.UserSummary> authors = userSummaryClient.findAll(
                posts.getContent().stream().map(PostEntity::getAuthorId).collect(Collectors.toSet()));
        List<ForumDtos.PostSummary> content = posts.getContent().stream()
                .map(post -> toPostSummary(post, authors))
                .toList();
        return toPageResponse(posts, content);
    }

    @Transactional
    public ForumDtos.CommentView createComment(Long userId,
                                                Long postId,
                                                ForumDtos.CreateCommentRequest request) {
        requireUser(userId);
        PostEntity post = requireActivePost(postId);
        if (request.parentCommentId() != null) {
            CommentEntity parent = commentRepository
                    .findByIdAndStatus(request.parentCommentId(), CommentEntity.Status.PUBLISHED)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.BAD_REQUEST, "PARENT_COMMENT_NOT_FOUND", "父评论不存在"));
            if (!parent.getPost().getId().equals(postId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PARENT_COMMENT_MISMATCH", "父评论不属于当前帖子");
            }
        }
        CommentEntity comment = commentRepository.save(new CommentEntity(
                post,
                userId,
                request.parentCommentId(),
                request.content().trim()));
        if (postRepository.incrementCommentCount(postId) == 0) {
            throw postNotFound();
        }
        return toCommentView(comment, userSummaryClient.findAll(Set.of(userId)));
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        requireUser(userId);
        CommentEntity comment = commentRepository
                .findByIdAndStatus(commentId, CommentEntity.Status.PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "评论不存在"));
        requireOwner(userId, comment.getAuthorId());
        Long postId = comment.getPost().getId();
        comment.delete(Instant.now());
        postRepository.decrementCommentCount(postId);
    }

    @Transactional(readOnly = true)
    public ForumDtos.PageResponse<ForumDtos.CommentView> listComments(Long postId, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        requireActivePost(postId);
        Page<CommentEntity> comments = commentRepository.findByPost_IdAndStatusOrderByCreatedAtAscIdAsc(
                postId,
                CommentEntity.Status.PUBLISHED,
                pageable);
        Map<Long, UserSummaryClient.UserSummary> authors = userSummaryClient.findAll(
                comments.getContent().stream().map(CommentEntity::getAuthorId).collect(Collectors.toSet()));
        List<ForumDtos.CommentView> content = comments.getContent().stream()
                .map(comment -> toCommentView(comment, authors))
                .toList();
        return toPageResponse(comments, content);
    }

    @Transactional
    public ForumDtos.LikeResponse like(Long userId, Long postId) {
        requireUser(userId);
        requireActivePost(postId);
        int inserted = postLikeRepository.insertIgnore(postId, userId);
        if (inserted > 0) {
            postRepository.incrementLikeCount(postId);
        }
        PostEntity post = requireActivePost(postId);
        return new ForumDtos.LikeResponse(postId, true, post.getLikeCount());
    }

    @Transactional
    public ForumDtos.LikeResponse unlike(Long userId, Long postId) {
        requireUser(userId);
        requireActivePost(postId);
        int deleted = postLikeRepository.deleteLike(postId, userId);
        if (deleted > 0) {
            postRepository.decrementLikeCount(postId);
        }
        PostEntity post = requireActivePost(postId);
        return new ForumDtos.LikeResponse(postId, false, post.getLikeCount());
    }

    private SectionEntity requireActiveSection(Long sectionId) {
        return sectionRepository.findByIdAndStatus(sectionId, SectionEntity.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SECTION_NOT_FOUND", "板块不存在或已隐藏"));
    }

    private PostEntity requireActivePost(Long postId) {
        return postRepository.findByIdAndStatus(postId, PostEntity.Status.PUBLISHED)
                .orElseThrow(this::postNotFound);
    }

    private ApiException postNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "帖子不存在");
    }

    private static void requireUser(Long userId) {
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
        }
    }

    private static void requireOwner(Long currentUserId, Long authorId) {
        if (!currentUserId.equals(authorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_POST_OWNER", "只能操作自己的内容");
        }
    }

    private static Pageable pageRequest(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "页码不能小于 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE", "每页数量必须在 1 到 100 之间");
        }
        return PageRequest.of(page, size);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private static <T> ForumDtos.PageResponse<T> toPageResponse(Page<?> page, List<T> content) {
        return new ForumDtos.PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private static ForumDtos.SectionView toSectionView(SectionEntity section) {
        return new ForumDtos.SectionView(
                section.getId(),
                section.getSlug(),
                section.getName(),
                section.getDescription(),
                section.getSortOrder());
    }

    private static ForumDtos.AuthorView toAuthorView(Long userId,
                                                       Map<Long, UserSummaryClient.UserSummary> authors) {
        UserSummaryClient.UserSummary author = authors.get(userId);
        if (author == null) {
            author = new UserSummaryClient.UserSummary(userId, "user-" + userId, "用户 " + userId, null);
        }
        return new ForumDtos.AuthorView(author.id(), author.username(), author.displayName(), author.avatarUrl());
    }

    private static ForumDtos.PostSummary toPostSummary(PostEntity post,
                                                        Map<Long, UserSummaryClient.UserSummary> authors) {
        return new ForumDtos.PostSummary(
                post.getId(),
                toSectionView(post.getSection()),
                toAuthorView(post.getAuthorId(), authors),
                post.getTitle(),
                post.getViewCount(),
                post.getCommentCount(),
                post.getLikeCount(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    private static ForumDtos.PostView toPostView(PostEntity post,
                                                  Long currentUserId,
                                                  Map<Long, UserSummaryClient.UserSummary> authors) {
        return toPostView(post, currentUserId, authors, false);
    }

    private static ForumDtos.PostView toPostView(PostEntity post,
                                                  Long currentUserId,
                                                  Map<Long, UserSummaryClient.UserSummary> authors,
                                                  boolean liked) {
        return new ForumDtos.PostView(
                post.getId(),
                toSectionView(post.getSection()),
                toAuthorView(post.getAuthorId(), authors),
                post.getTitle(),
                post.getContent(),
                post.getViewCount(),
                post.getCommentCount(),
                post.getLikeCount(),
                liked,
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    private static ForumDtos.CommentView toCommentView(CommentEntity comment,
                                                        Map<Long, UserSummaryClient.UserSummary> authors) {
        return new ForumDtos.CommentView(
                comment.getId(),
                comment.getPost().getId(),
                comment.getParentCommentId(),
                toAuthorView(comment.getAuthorId(), authors),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}