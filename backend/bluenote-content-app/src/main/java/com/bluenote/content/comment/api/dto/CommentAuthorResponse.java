package com.bluenote.content.comment.api.dto;

public record CommentAuthorResponse(
        String userId,
        String nickname,
        String avatarUrl,
        String userStatus
) {
}
