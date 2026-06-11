package com.bluenote.content.comment.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.content.comment.api.dto.BatchCommentSummaryRequest;
import com.bluenote.content.comment.api.dto.BatchCommentSummaryResponse;
import com.bluenote.content.comment.api.dto.CommentCounterSourceRequest;
import com.bluenote.content.comment.api.dto.CommentCounterSourceResponse;
import com.bluenote.content.comment.application.CommentApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/comments")
public class InternalCommentController {

    private final CommentApplicationService commentApplicationService;

    public InternalCommentController(CommentApplicationService commentApplicationService) {
        this.commentApplicationService = commentApplicationService;
    }

    @PostMapping("/batch-summary")
    public ApiResponse<BatchCommentSummaryResponse> batchSummary(@Valid @RequestBody BatchCommentSummaryRequest request) {
        return ApiResponse.success(commentApplicationService.batchSummary(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/counter-source")
    public ApiResponse<CommentCounterSourceResponse> counterSource(@Valid @RequestBody CommentCounterSourceRequest request) {
        return ApiResponse.success(commentApplicationService.counterSource(request), TraceIdHolder.currentOrNew());
    }
}
