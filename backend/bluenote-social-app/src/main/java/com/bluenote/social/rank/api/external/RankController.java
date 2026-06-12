package com.bluenote.social.rank.api.external;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import com.bluenote.social.rank.api.dto.RankDtos.CreatorRankResponse;
import com.bluenote.social.rank.api.dto.RankDtos.WeeklyHotNotesResponse;
import com.bluenote.social.rank.api.dto.RankDtos.YearlyCreatorGrowthResponse;
import com.bluenote.social.rank.application.RankApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ranks")
public class RankController {

    private final RankApplicationService rankApplicationService;

    public RankController(RankApplicationService rankApplicationService) {
        this.rankApplicationService = rankApplicationService;
    }

    @GetMapping("/weekly-hot-notes")
    public ApiResponse<WeeklyHotNotesResponse> weeklyHotNotes(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                rankApplicationService.weeklyHotNotes(optionalUserId(), cursor, size),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/yearly-creator-growth")
    public ApiResponse<YearlyCreatorGrowthResponse> yearlyCreatorGrowth(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.success(
                rankApplicationService.yearlyCreatorGrowth(cursor, size),
                TraceIdHolder.currentOrNew()
        );
    }

    @GetMapping("/creators/me/yearly-growth-rank")
    public ApiResponse<CreatorRankResponse> myYearlyGrowthRank() {
        return ApiResponse.success(
                rankApplicationService.myYearlyGrowthRank(requireUserId()),
                TraceIdHolder.currentOrNew()
        );
    }

    private String optionalUserId() {
        UserContext userContext = UserContextHolder.current();
        if (userContext == null || !userContext.authenticated()) {
            return null;
        }
        return userContext.userId();
    }

    private String requireUserId() {
        UserContext userContext = UserContextHolder.current();
        if (userContext == null || !userContext.authenticated()) {
            throw new BusinessException(ApiErrorCode.ACCESS_TOKEN_INVALID);
        }
        return userContext.userId();
    }
}
