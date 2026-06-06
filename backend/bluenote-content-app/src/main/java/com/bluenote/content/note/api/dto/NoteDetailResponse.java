package com.bluenote.content.note.api.dto;

import java.util.List;

public record NoteDetailResponse(
        String noteId,
        NoteAuthorResponse author,
        String title,
        String content,
        String visibility,
        String noteStatus,
        Integer currentVersion,
        Boolean commentEnabled,
        List<NoteMediaResponse> mediaFiles,
        List<String> topics,
        NoteCountsResponse counts,
        ViewerActionResponse viewerAction,
        String publishedAt,
        boolean degraded
) {
}

