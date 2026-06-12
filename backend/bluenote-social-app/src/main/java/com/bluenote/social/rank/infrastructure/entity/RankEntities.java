package com.bluenote.social.rank.infrastructure.entity;

import java.time.LocalDateTime;

public final class RankEntities {

    private RankEntities() {
    }

    public static class RankPeriodEntity {
        private Long id;
        private String rankCode;
        private String periodId;
        private String periodType;
        private String periodStatus;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private LocalDateTime frozenAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getRankCode() { return rankCode; }
        public void setRankCode(String rankCode) { this.rankCode = rankCode; }
        public String getPeriodId() { return periodId; }
        public void setPeriodId(String periodId) { this.periodId = periodId; }
        public String getPeriodType() { return periodType; }
        public void setPeriodType(String periodType) { this.periodType = periodType; }
        public String getPeriodStatus() { return periodStatus; }
        public void setPeriodStatus(String periodStatus) { this.periodStatus = periodStatus; }
        public LocalDateTime getStartAt() { return startAt; }
        public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
        public LocalDateTime getEndAt() { return endAt; }
        public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
        public LocalDateTime getFrozenAt() { return frozenAt; }
        public void setFrozenAt(LocalDateTime frozenAt) { this.frozenAt = frozenAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class RankNoteIndexEntity {
        private Long noteId;
        private Long authorId;
        private String titleSnapshot;
        private String coverUrlSnapshot;
        private String visibility;
        private String noteStatus;
        private String eligibleStatus;
        private LocalDateTime publishedAt;
        private String lastEventId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getNoteId() { return noteId; }
        public void setNoteId(Long noteId) { this.noteId = noteId; }
        public Long getAuthorId() { return authorId; }
        public void setAuthorId(Long authorId) { this.authorId = authorId; }
        public String getTitleSnapshot() { return titleSnapshot; }
        public void setTitleSnapshot(String titleSnapshot) { this.titleSnapshot = titleSnapshot; }
        public String getCoverUrlSnapshot() { return coverUrlSnapshot; }
        public void setCoverUrlSnapshot(String coverUrlSnapshot) { this.coverUrlSnapshot = coverUrlSnapshot; }
        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }
        public String getNoteStatus() { return noteStatus; }
        public void setNoteStatus(String noteStatus) { this.noteStatus = noteStatus; }
        public String getEligibleStatus() { return eligibleStatus; }
        public void setEligibleStatus(String eligibleStatus) { this.eligibleStatus = eligibleStatus; }
        public LocalDateTime getPublishedAt() { return publishedAt; }
        public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
        public String getLastEventId() { return lastEventId; }
        public void setLastEventId(String lastEventId) { this.lastEventId = lastEventId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class RankMemberScoreEntity {
        private Long id;
        private String rankCode;
        private String periodId;
        private String memberType;
        private Long memberId;
        private Long score;
        private Double rankScore;
        private Integer scoreVersion;
        private String memberStatus;
        private LocalDateTime lastScoreAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getRankCode() { return rankCode; }
        public void setRankCode(String rankCode) { this.rankCode = rankCode; }
        public String getPeriodId() { return periodId; }
        public void setPeriodId(String periodId) { this.periodId = periodId; }
        public String getMemberType() { return memberType; }
        public void setMemberType(String memberType) { this.memberType = memberType; }
        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public Long getScore() { return score; }
        public void setScore(Long score) { this.score = score; }
        public Double getRankScore() { return rankScore; }
        public void setRankScore(Double rankScore) { this.rankScore = rankScore; }
        public Integer getScoreVersion() { return scoreVersion; }
        public void setScoreVersion(Integer scoreVersion) { this.scoreVersion = scoreVersion; }
        public String getMemberStatus() { return memberStatus; }
        public void setMemberStatus(String memberStatus) { this.memberStatus = memberStatus; }
        public LocalDateTime getLastScoreAt() { return lastScoreAt; }
        public void setLastScoreAt(LocalDateTime lastScoreAt) { this.lastScoreAt = lastScoreAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class RankScoreContributionEntity {
        private Long id;
        private String rankCode;
        private String periodId;
        private String memberType;
        private Long memberId;
        private String sourceType;
        private Long sourceId;
        private Long score;
        private String contributionStatus;
        private String lastEventId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getRankCode() { return rankCode; }
        public void setRankCode(String rankCode) { this.rankCode = rankCode; }
        public String getPeriodId() { return periodId; }
        public void setPeriodId(String periodId) { this.periodId = periodId; }
        public String getMemberType() { return memberType; }
        public void setMemberType(String memberType) { this.memberType = memberType; }
        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public Long getSourceId() { return sourceId; }
        public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
        public Long getScore() { return score; }
        public void setScore(Long score) { this.score = score; }
        public String getContributionStatus() { return contributionStatus; }
        public void setContributionStatus(String contributionStatus) { this.contributionStatus = contributionStatus; }
        public String getLastEventId() { return lastEventId; }
        public void setLastEventId(String lastEventId) { this.lastEventId = lastEventId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class RankConsumeRecordEntity {
        private Long id;
        private String consumerGroup;
        private String eventId;
        private String topic;
        private String eventType;
        private String bizKey;
        private String consumeStatus;
        private Integer retryCount;
        private String errorMessage;
        private LocalDateTime consumedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getConsumerGroup() { return consumerGroup; }
        public void setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; }
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getBizKey() { return bizKey; }
        public void setBizKey(String bizKey) { this.bizKey = bizKey; }
        public String getConsumeStatus() { return consumeStatus; }
        public void setConsumeStatus(String consumeStatus) { this.consumeStatus = consumeStatus; }
        public Integer getRetryCount() { return retryCount; }
        public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public LocalDateTime getConsumedAt() { return consumedAt; }
        public void setConsumedAt(LocalDateTime consumedAt) { this.consumedAt = consumedAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class RankOutboxEventEntity {
        private String eventId;
        private String eventType;
        private String aggregateId;
        private String payload;
        private String sendStatus;
        private Integer retryCount;
        private LocalDateTime nextRetryAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getAggregateId() { return aggregateId; }
        public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public String getSendStatus() { return sendStatus; }
        public void setSendStatus(String sendStatus) { this.sendStatus = sendStatus; }
        public Integer getRetryCount() { return retryCount; }
        public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
        public LocalDateTime getNextRetryAt() { return nextRetryAt; }
        public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class RankRebuildTaskEntity {
        private String taskId;
        private String taskType;
        private String rankCode;
        private String periodId;
        private String taskStatus;
        private String progressJson;
        private String reason;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public String getRankCode() { return rankCode; }
        public void setRankCode(String rankCode) { this.rankCode = rankCode; }
        public String getPeriodId() { return periodId; }
        public void setPeriodId(String periodId) { this.periodId = periodId; }
        public String getTaskStatus() { return taskStatus; }
        public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
        public String getProgressJson() { return progressJson; }
        public void setProgressJson(String progressJson) { this.progressJson = progressJson; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class RankSnapshotEntity {
        private Long snapshotId;
        private String rankCode;
        private String periodId;
        private String snapshotType;
        private Integer itemCount;
        private String sourceVersion;
        private LocalDateTime createdAt;

        public Long getSnapshotId() { return snapshotId; }
        public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
        public String getRankCode() { return rankCode; }
        public void setRankCode(String rankCode) { this.rankCode = rankCode; }
        public String getPeriodId() { return periodId; }
        public void setPeriodId(String periodId) { this.periodId = periodId; }
        public String getSnapshotType() { return snapshotType; }
        public void setSnapshotType(String snapshotType) { this.snapshotType = snapshotType; }
        public Integer getItemCount() { return itemCount; }
        public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
        public String getSourceVersion() { return sourceVersion; }
        public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class RankSnapshotItemEntity {
        private Long id;
        private Long snapshotId;
        private Integer rankNo;
        private String memberType;
        private Long memberId;
        private Long score;
        private Double rankScore;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getSnapshotId() { return snapshotId; }
        public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
        public Integer getRankNo() { return rankNo; }
        public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
        public String getMemberType() { return memberType; }
        public void setMemberType(String memberType) { this.memberType = memberType; }
        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public Long getScore() { return score; }
        public void setScore(Long score) { this.score = score; }
        public Double getRankScore() { return rankScore; }
        public void setRankScore(Double rankScore) { this.rankScore = rankScore; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}
