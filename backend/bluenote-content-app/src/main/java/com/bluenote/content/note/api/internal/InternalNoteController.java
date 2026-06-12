package com.bluenote.content.note.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.content.note.api.dto.AuthorRecentNotesRequest;
import com.bluenote.content.note.api.dto.AuthorRecentNotesResponse;
import com.bluenote.content.note.api.dto.BatchNoteSummaryRequest;
import com.bluenote.content.note.api.dto.BatchNoteSummaryResponse;
import com.bluenote.content.note.api.dto.CommentCheckRequest;
import com.bluenote.content.note.api.dto.CommentCheckResponse;
import com.bluenote.content.note.api.dto.NoteCounterSourceRequest;
import com.bluenote.content.note.api.dto.NoteCounterSourceResponse;
import com.bluenote.content.note.application.NoteApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notes")
public class InternalNoteController {

    private final NoteApplicationService noteApplicationService;

    public InternalNoteController(NoteApplicationService noteApplicationService) {
        this.noteApplicationService = noteApplicationService;
    }

    @PostMapping("/batch-summary")
    public ApiResponse<BatchNoteSummaryResponse> batchSummary(@Valid @RequestBody BatchNoteSummaryRequest request) {
        return ApiResponse.success(noteApplicationService.batchSummary(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/authors/recent")
    public ApiResponse<AuthorRecentNotesResponse> authorRecentNotes(@Valid @RequestBody AuthorRecentNotesRequest request) {
        return ApiResponse.success(noteApplicationService.authorRecentNotes(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/comment-check")
    public ApiResponse<CommentCheckResponse> commentCheck(@Valid @RequestBody CommentCheckRequest request) {
        return ApiResponse.success(noteApplicationService.commentCheck(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/counter-source")
    public ApiResponse<NoteCounterSourceResponse> counterSource(@Valid @RequestBody NoteCounterSourceRequest request) {
        return ApiResponse.success(noteApplicationService.counterSource(request), TraceIdHolder.currentOrNew());
    }
}
