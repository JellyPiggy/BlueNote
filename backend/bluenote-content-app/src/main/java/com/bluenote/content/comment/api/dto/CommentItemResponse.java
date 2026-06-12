package com.bluenote.content.comment.api.dto;

public record CommentItemResponse(
        String commentId,
        String noteId,
        String rootId,
        String parentCommentId,
        CommentAuthorResponse replyToUser,
        CommentAuthorResponse author,
        String content,
        int level,
        String commentStatus,
        long likeCount,
        long replyCount,
        CommentViewerActionResponse viewerAction,
        String createdAt,
        boolean degraded
) {
}
