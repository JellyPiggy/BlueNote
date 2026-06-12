package com.bluenote.social.rank.api.internal;

import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.social.rank.api.dto.RankDtos.RankConsumeEventRequest;
import com.bluenote.social.rank.api.dto.RankDtos.RankConsumeEventResponse;
import com.bluenote.social.rank.api.dto.RankDtos.RankMemberRankRequest;
import com.bluenote.social.rank.api.dto.RankDtos.RankMemberRankResponse;
import com.bluenote.social.rank.api.dto.RankDtos.RankRebuildRequest;
import com.bluenote.social.rank.api.dto.RankDtos.RankRebuildTaskResponse;
import com.bluenote.social.rank.api.dto.RankDtos.RankSnapshotDetailResponse;
import com.bluenote.social.rank.api.dto.RankDtos.RankSnapshotRequest;
import com.bluenote.social.rank.api.dto.RankDtos.RankSnapshotResponse;
import com.bluenote.social.rank.application.RankApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ranks")
public class InternalRankController {

    private final RankApplicationService rankApplicationService;

    public InternalRankController(RankApplicationService rankApplicationService) {
        this.rankApplicationService = rankApplicationService;
    }

    @PostMapping("/members/batch-rank")
    public ApiResponse<RankMemberRankResponse> batchRank(@RequestBody RankMemberRankRequest request) {
        return ApiResponse.success(rankApplicationService.batchRank(request), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/rebuild")
    public ApiResponse<RankRebuildTaskResponse> rebuild(@RequestBody(required = false) RankRebuildRequest request) {
        return ApiResponse.success(rankApplicationService.rebuild(request), TraceIdHolder.currentOrNew());
    }

    @GetMapping("/rebuild-tasks/{taskId}")
    public ApiResponse<RankRebuildTaskResponse> rebuildTask(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(rankApplicationService.rebuildTask(taskId), TraceIdHolder.currentOrNew());
    }

    @PostMapping("/snapshots")
    public ApiResponse<RankSnapshotResponse> createSnapshot(@RequestBody(required = false) RankSnapshotRequest request) {
        return ApiResponse.success(rankApplicationService.createSnapshot(request), TraceIdHolder.currentOrNew());
    }

    @GetMapping("/snapshots/latest")
    public ApiResponse<RankSnapshotDetailResponse> latestSnapshot(
            @RequestParam("rankCode") String rankCode,
            @RequestParam("periodId") String periodId,
            @RequestParam(value = "snapshotType", required = false) String snapshotType
    ) {
        return ApiResponse.success(
                rankApplicationService.latestSnapshot(rankCode, periodId, snapshotType),
                TraceIdHolder.currentOrNew()
        );
    }

    @PostMapping("/events/consume")
    public ApiResponse<RankConsumeEventResponse> consumeEvent(@RequestBody RankConsumeEventRequest request) {
        return ApiResponse.success(rankApplicationService.consumeEvent(request), TraceIdHolder.currentOrNew());
    }
}
