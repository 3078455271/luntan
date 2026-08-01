package com.luntan.forum.api;

import com.luntan.forum.api.dto.ForumDtos;
import com.luntan.forum.application.ForumService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final ForumService forumService;

    public PostController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping
    public ForumDtos.PageResponse<ForumDtos.PostSummary> search(
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return forumService.searchPosts(sectionId, authorId, keyword, page, size);
    }

    @GetMapping("/{postId}")
    public ForumDtos.PostView get(@PathVariable Long postId,
                                  @AuthenticationPrincipal Jwt jwt) {
        return forumService.getPost(postId, userId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ForumDtos.PostView create(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody ForumDtos.CreatePostRequest request) {
        return forumService.createPost(userId(jwt), request);
    }

    @PutMapping("/{postId}")
    public ForumDtos.PostView update(@PathVariable Long postId,
                                     @AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody ForumDtos.UpdatePostRequest request) {
        return forumService.updatePost(userId(jwt), postId, request);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long postId,
                       @AuthenticationPrincipal Jwt jwt) {
        forumService.deletePost(userId(jwt), postId);
    }

    @PostMapping("/{postId}/like")
    public ForumDtos.LikeResponse like(@PathVariable Long postId,
                                       @AuthenticationPrincipal Jwt jwt) {
        return forumService.like(userId(jwt), postId);
    }

    @DeleteMapping("/{postId}/like")
    public ForumDtos.LikeResponse unlike(@PathVariable Long postId,
                                         @AuthenticationPrincipal Jwt jwt) {
        return forumService.unlike(userId(jwt), postId);
    }

    private static Long userId(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }
}