package com.bluenote.content.note.api.external;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.core.CursorPage;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.content.note.api.dto.DeleteNoteResponse;
import com.bluenote.content.note.api.dto.DraftNoteResponse;
import com.bluenote.content.note.api.dto.NoteCardResponse;
import com.bluenote.content.note.api.dto.NoteDetailResponse;
import com.bluenote.content.note.api.dto.PublishNoteRequest;
import com.bluenote.content.note.api.dto.PublishNoteResponse;
import com.bluenote.content.note.api.dto.UpsertNoteRequest;
import com.bluenote.content.note.application.NoteApplicationService;
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
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteApplicationService noteApplicationService;

    public NoteController(NoteApplicationService noteApplicationService) {
        this.noteApplicationService = noteApplicationService;
    }

    @PostMapping("/drafts")
    public ApiResponse<DraftNoteResponse> saveDraft(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpsertNoteRequest request
    ) {
        return ApiResponse.success(
                noteApplicationService.saveDraft(requireUserId(), idempotencyKey, request),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping
    public ApiResponse<PublishNoteResponse> publishNewNote(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpsertNoteRequest request
    ) {
        return ApiResponse.success(
                noteApplicationService.publishNewNote(requireUserId(), idempotencyKey, request),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/{noteId}/publish")
    public ApiResponse<PublishNoteResponse> publishDraft(
            @PathVariable("noteId") String noteId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PublishNoteRequest request
    ) {
        return ApiResponse.success(
                noteApplicationService.publishDraft(requireUserId(), noteId, idempotencyKey, request),
                TraceIdHolder.currentOrNew()
        );
    }

    @DeleteMapping("/{noteId}")
    public ApiResponse<DeleteNoteResponse> deleteNote(@PathVariable("noteId") String noteId) {
        return ApiResponse.success(
                noteApplicationService.deleteNote(requireUserId(), noteId),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/{noteId}")
    public ApiResponse<NoteDetailResponse> detail(@PathVariable("noteId") String noteId) {
        return ApiResponse.success(
                noteApplicationService.detail(noteId, optionalUserId()),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<CursorPage<NoteCardResponse>> authorNotes(
            @PathVariable("userId") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                noteApplicationService.authorNotes(userId, cursor, size),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/me")
    public ApiResponse<CursorPage<NoteCardResponse>> myNotes(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                noteApplicationService.myNotes(requireUserId(), status, cursor, size),
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

