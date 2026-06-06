package com.bluenote.content.file.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.content.file.api.dto.InternalBatchBindFileRequest;
import com.bluenote.content.file.api.dto.InternalBatchBindFileResponse;
import com.bluenote.content.file.api.dto.InternalBatchValidateFileRequest;
import com.bluenote.content.file.api.dto.InternalBatchValidateFileResponse;
import com.bluenote.content.file.api.dto.InternalBindFileRequest;
import com.bluenote.content.file.api.dto.InternalBindFileResponse;
import com.bluenote.content.file.api.dto.InternalValidateFileRequest;
import com.bluenote.content.file.api.dto.InternalValidateFileResponse;
import com.bluenote.content.file.application.FileApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/files")
public class InternalFileController {

    private final FileApplicationService fileApplicationService;

    public InternalFileController(FileApplicationService fileApplicationService) {
        this.fileApplicationService = fileApplicationService;
    }

    @PostMapping("/validate")
    public ApiResponse<InternalValidateFileResponse> validateFile(
            @Valid @RequestBody InternalValidateFileRequest request
    ) {
        return ApiResponse.success(fileApplicationService.validateFile(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/batch-validate")
    public ApiResponse<InternalBatchValidateFileResponse> batchValidateFiles(
            @Valid @RequestBody InternalBatchValidateFileRequest request
    ) {
        return ApiResponse.success(fileApplicationService.batchValidateFiles(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/bind")
    public ApiResponse<InternalBindFileResponse> bindFile(@Valid @RequestBody InternalBindFileRequest request) {
        return ApiResponse.success(fileApplicationService.bindFile(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/batch-bind")
    public ApiResponse<InternalBatchBindFileResponse> batchBindFiles(
            @Valid @RequestBody InternalBatchBindFileRequest request
    ) {
        return ApiResponse.success(fileApplicationService.batchBindFiles(request), TraceIdHolder.currentOrNew());
    }
}

