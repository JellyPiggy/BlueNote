package com.bluenote.content.comment.api.external;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.core.CursorPage;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.content.comment.api.dto.CommentCursorPage;
import com.bluenote.content.comment.api.dto.CommentItemResponse;
import com.bluenote.content.comment.api.dto.CommentLikeResponse;
import com.bluenote.content.comment.api.dto.CreateCommentRequest;
import com.bluenote.content.comment.api.dto.CreateCommentResponse;
import com.bluenote.content.comment.api.dto.DeleteCommentResponse;
import com.bluenote.content.comment.api.dto.MyCommentItemResponse;
import com.bluenote.content.comment.application.CommentApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentApplicationService commentApplicationService;

    public CommentController(CommentApplicationService commentApplicationService) {
        this.commentApplicationService = commentApplicationService;
    }

    @PostMapping("/notes/{noteId}")
    public ApiResponse<CreateCommentResponse> createRootComment(
            @PathVariable("noteId") String noteId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return ApiResponse.success(
                commentApplicationService.createRootComment(requireUserId(), noteId, idempotencyKey, request),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/{commentId}/replies")
    public ApiResponse<CreateCommentResponse> replyComment(
            @PathVariable("commentId") String commentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return ApiResponse.success(
                commentApplicationService.replyComment(requireUserId(), commentId, idempotencyKey, request),
                TraceIdHolder.currentOrNew()
        );
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<DeleteCommentResponse> deleteComment(@PathVariable("commentId") String commentId) {
        return ApiResponse.success(
                commentApplicationService.deleteComment(requireUserId(), commentId),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/notes/{noteId}")
    public ApiResponse<CommentCursorPage<CommentItemResponse>> noteComments(
            @PathVariable("noteId") String noteId,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                commentApplicationService.noteComments(noteId, sort, cursor, size, optionalUserId()),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/{rootCommentId}/replies")
    public ApiResponse<CommentCursorPage<CommentItemResponse>> replies(
            @PathVariable("rootCommentId") String rootCommentId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                commentApplicationService.replies(rootCommentId, cursor, size, optionalUserId()),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/{commentId}/like")
    public ApiResponse<CommentLikeResponse> like(@PathVariable("commentId") String commentId) {
        return ApiResponse.success(
                commentApplicationService.like(requireUserId(), commentId),
                TraceIdHolder.currentOrNew()
        );
    }

    @DeleteMapping("/{commentId}/like")
    public ApiResponse<CommentLikeResponse> unlike(@PathVariable("commentId") String commentId) {
        return ApiResponse.success(
                commentApplicationService.unlike(requireUserId(), commentId),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/me")
    public ApiResponse<CursorPage<MyCommentItemResponse>> myComments(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                commentApplicationService.myComments(requireUserId(), cursor, size),
                TraceIdHolder.currentOrNew()
        );
    }

    private String requireUserId() {
        UserContext userContext = UserContextHolder.current();
        if (userContext == null || !userContext.authenticated()) {
            throw new BusinessException(ApiErrorCode.ACCESS_TOKEN_INVALID);
        }
        return userContext.userId();
    }

    private String optionalUserId() {
        UserContext userContext = UserContextHolder.current();
        return userContext == null ? null : userContext.userId();
    }
}
