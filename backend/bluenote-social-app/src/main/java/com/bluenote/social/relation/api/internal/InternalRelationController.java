package com.bluenote.social.relation.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.relation.api.dto.BatchFollowStatusResponse;
import com.bluenote.social.relation.api.dto.CounterSourceRequest;
import com.bluenote.social.relation.api.dto.CounterSourceResponse;
import com.bluenote.social.relation.api.dto.InternalFollowerPageItem;
import com.bluenote.social.relation.api.dto.InternalFollowingPageItem;
import com.bluenote.social.relation.api.dto.InternalRelationPageResponse;
import com.bluenote.social.relation.application.RelationApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/relations")
public class InternalRelationController {

    private final RelationApplicationService relationApplicationService;

    public InternalRelationController(RelationApplicationService relationApplicationService) {
        this.relationApplicationService = relationApplicationService;
    }

    @PostMapping("/following/status/batch")
    public ApiResponse<BatchFollowStatusResponse> batchStatus(@Valid @RequestBody InternalBatchStatusRequest request) {
        return ApiResponse.success(
                relationApplicationService.batchStatus(request.viewerId(), request.targetUserIds()),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/users/{userId}/followers/page")
    public ApiResponse<InternalRelationPageResponse<InternalFollowerPageItem>> followersPage(
            @PathVariable("userId") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                relationApplicationService.internalFollowersPage(userId, cursor, size),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/users/{userId}/following/page")
    public ApiResponse<InternalRelationPageResponse<InternalFollowingPageItem>> followingPage(
            @PathVariable("userId") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                relationApplicationService.internalFollowingPage(userId, cursor, size),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/counter-source")
    public ApiResponse<CounterSourceResponse> counterSource(@Valid @RequestBody CounterSourceRequest request) {
        return ApiResponse.success(relationApplicationService.counterSource(request), TraceIdHolder.currentOrNew());
    }

    public record InternalBatchStatusRequest(
            @NotBlank
            String viewerId,

            @NotEmpty
            @Size(max = 100)
            List<String> targetUserIds
    ) {
    }
}
