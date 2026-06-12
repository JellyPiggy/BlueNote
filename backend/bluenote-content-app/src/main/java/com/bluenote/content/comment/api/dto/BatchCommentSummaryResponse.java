package com.bluenote.content.comment.api.dto;

import java.util.List;

public record BatchCommentSummaryResponse(List<CommentSummaryItem> comments) {
}
