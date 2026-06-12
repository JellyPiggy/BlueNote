package com.bluenote.social.rank.api.dto;

import java.util.List;
import java.util.Map;

public final class RankDtos {

    private RankDtos() {
    }

    public record RankCounts(Long likeCount, Long commentCount, Long collectCount) {
    }

    public record WeeklyHotNoteItem(
            Integer rankNo,
            String noteId,
            String authorId,
            String authorNickname,
            String authorAvatarUrl,
            String title,
            String coverUrl,
            Long score,
            RankCounts counts,
            String publishedAt
    ) {
    }

    public record WeeklyHotNotesResponse(
            String rankCode,
            String periodId,
            List<WeeklyHotNoteItem> items,
            String nextCursor,
            Boolean hasMore,
            Boolean degraded
    ) {
    }

    public record CreatorStats(Long publicNoteCount, Long followerCount, Long likedCount) {
    }

    public record YearlyCreatorItem(
            Integer rankNo,
            String creatorId,
            String nickname,
            String avatarUrl,
            String bio,
            Long score,
            String rankMode,
            CreatorStats stats
    ) {
    }

    public record YearlyCreatorGrowthResponse(
            String rankCode,
            String periodId,
            List<YearlyCreatorItem> items,
            String nextCursor,
            Boolean hasMore,
            Boolean degraded
    ) {
    }

    public record CreatorRankResponse(
            String rankCode,
            String periodId,
            String creatorId,
            Integer rankNo,
            String rankMode,
            Long score,
            Boolean notRanked,
            Boolean degraded
    ) {
    }

    public record RankMemberRankRequest(
            String rankCode,
            String periodId,
            String memberType,
            List<String> memberIds
    ) {
    }

    public record RankMemberRankItem(
            String memberType,
            String memberId,
            Integer rankNo,
            String rankMode,
            Long score,
            Boolean notRanked
    ) {
    }

    public record RankMemberRankResponse(
            String rankCode,
            String periodId,
            List<RankMemberRankItem> members,
            Boolean degraded
    ) {
    }

    public record RankRebuildRequest(String rankCode, String periodId, String taskType, String reason) {
    }

    public record RankProgress(Integer total, Integer success, Integer failed) {
    }

    public record RankRebuildTaskResponse(
            String taskId,
            String rankCode,
            String periodId,
            String taskType,
            String taskStatus,
            RankProgress progress
    ) {
    }

    public record RankSnapshotRequest(String rankCode, String periodId, String snapshotType, String reason) {
    }

    public record RankSnapshotResponse(
            String snapshotId,
            String rankCode,
            String periodId,
            String snapshotType,
            Integer itemCount,
            String createdAt
    ) {
    }

    public record RankSnapshotItem(Integer rankNo, String memberType, String memberId, Long score) {
    }

    public record RankSnapshotDetailResponse(
            String snapshotId,
            String rankCode,
            String periodId,
            String snapshotType,
            List<RankSnapshotItem> items,
            String createdAt
    ) {
    }

    public record RankConsumeEventRequest(
            String topic,
            String consumerGroup,
            String eventId,
            String eventType,
            Integer eventVersion,
            String occurredAt,
            String traceId,
            String producer,
            String bizKey,
            Map<String, Object> payload
    ) {
    }

    public record RankConsumeEventResponse(
            String eventId,
            String eventType,
            String consumeStatus,
            Integer updatedRanks
    ) {
    }
}
