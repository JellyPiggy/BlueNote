package com.bluenote.content.file.api.external;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.content.file.api.dto.ConfirmUploadRequest;
import com.bluenote.content.file.api.dto.ConfirmUploadResponse;
import com.bluenote.content.file.api.dto.CreateUploadTokenRequest;
import com.bluenote.content.file.api.dto.FileAccessUrlResponse;
import com.bluenote.content.file.api.dto.UploadTokenResponse;
import com.bluenote.content.file.application.FileApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileApplicationService fileApplicationService;

    public FileController(FileApplicationService fileApplicationService) {
        this.fileApplicationService = fileApplicationService;
    }

    @PostMapping("/upload-token")
    public ApiResponse<UploadTokenResponse> createUploadToken(@Valid @RequestBody CreateUploadTokenRequest request) {
        return ApiResponse.success(
                fileApplicationService.createUploadToken(requireUserId(), request),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/{fileId}/confirm")
    public ApiResponse<ConfirmUploadResponse> confirmUpload(
            @PathVariable("fileId") String fileId,
            @Valid @RequestBody ConfirmUploadRequest request
    ) {
        return ApiResponse.success(
                fileApplicationService.confirmUpload(requireUserId(), fileId, request),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/{fileId}/access-url")
    public ApiResponse<FileAccessUrlResponse> accessUrl(
            @PathVariable("fileId") String fileId,
            @RequestParam(value = "expireSeconds", required = false) Integer expireSeconds
    ) {
        return ApiResponse.success(
                fileApplicationService.accessUrl(optionalUserId(), fileId, expireSeconds),
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

