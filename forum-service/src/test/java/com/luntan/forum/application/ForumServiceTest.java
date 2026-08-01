package com.luntan.forum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.luntan.forum.api.dto.ForumDtos;
import com.luntan.forum.domain.ApiException;
import com.luntan.forum.infrastructure.client.UserSummaryClient;
import com.luntan.forum.infrastructure.persistence.CommentRepository;
import com.luntan.forum.infrastructure.persistence.PostEntity;
import com.luntan.forum.infrastructure.persistence.PostLikeRepository;
import com.luntan.forum.infrastructure.persistence.PostRepository;
import com.luntan.forum.infrastructure.persistence.SectionRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForumServiceTest {

    @Mock
    private SectionRepository sectionRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private UserSummaryClient userSummaryClient;

    private ForumService service;

    @BeforeEach
    void setUp() {
        service = new ForumService(
                sectionRepository,
                postRepository,
                commentRepository,
                postLikeRepository,
                userSummaryClient);
    }

    @Test
    void repeatedLikeOnlyIncrementsCounterOnce() {
        PostEntity post = org.mockito.Mockito.mock(PostEntity.class);
        when(postRepository.findByIdAndStatus(9L, PostEntity.Status.PUBLISHED))
                .thenReturn(Optional.of(post));
        when(postLikeRepository.insertIgnore(9L, 7L)).thenReturn(1, 0);
        when(post.getLikeCount()).thenReturn(1L, 1L);

        ForumDtos.LikeResponse first = service.like(7L, 9L);
        ForumDtos.LikeResponse second = service.like(7L, 9L);

        assertThat(first.liked()).isTrue();
        assertThat(second.likeCount()).isEqualTo(1L);
        verify(postRepository).incrementLikeCount(9L);
    }

    @Test
    void unlikeOnlyDecrementsWhenAStoredLikeExists() {
        PostEntity post = org.mockito.Mockito.mock(PostEntity.class);
        when(postRepository.findByIdAndStatus(9L, PostEntity.Status.PUBLISHED))
                .thenReturn(Optional.of(post));
        when(postLikeRepository.deleteLike(9L, 7L)).thenReturn(1, 0);
        when(post.getLikeCount()).thenReturn(0L, 0L);

        service.unlike(7L, 9L);
        service.unlike(7L, 9L);

        verify(postRepository).decrementLikeCount(9L);
    }

    @Test
    void unauthenticatedWriteIsRejectedBeforeRepositoryAccess() {
        ForumDtos.CreatePostRequest request = new ForumDtos.CreatePostRequest(1L, "title", "content");

        assertThatThrownBy(() -> service.createPost(null, request))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        verify(sectionRepository, never()).findByIdAndStatus(any(), any());
    }

    @Test
    void onlyAuthorCanDeletePost() {
        PostEntity post = org.mockito.Mockito.mock(PostEntity.class);
        when(post.getAuthorId()).thenReturn(8L);
        when(postRepository.findByIdAndStatus(9L, PostEntity.Status.PUBLISHED))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.deletePost(7L, 9L))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        verify(post, never()).delete(any(Instant.class));
    }

    @Test
    void authorDeleteTransitionsPostToDeletedState() {
        PostEntity post = new PostEntity(null, 7L, "title", "content");
        when(postRepository.findByIdAndStatus(9L, PostEntity.Status.PUBLISHED))
                .thenReturn(Optional.of(post));

        service.deletePost(7L, 9L);

        assertThat(post.getStatus()).isEqualTo(PostEntity.Status.DELETED);
    }

    @Test
    void invalidPaginationIsRejectedAtApplicationBoundary() {
        assertThatThrownBy(() -> service.searchPosts(null, null, null, -1, 20))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_PAGE");
        assertThatThrownBy(() -> service.listComments(9L, 0, 101))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_PAGE_SIZE");
        verify(postRepository, never()).search(any(), any(), any(), any());
    }
}