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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {

    private final ForumService forumService;

    public CommentController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping
    public ForumDtos.PageResponse<ForumDtos.CommentView> list(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return forumService.listComments(postId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ForumDtos.CommentView create(
            @PathVariable Long postId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ForumDtos.CreateCommentRequest request) {
        return forumService.createComment(userId(jwt), postId, request);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long commentId,
                       @AuthenticationPrincipal Jwt jwt) {
        forumService.deleteComment(userId(jwt), commentId);
    }

    private static Long userId(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }
}