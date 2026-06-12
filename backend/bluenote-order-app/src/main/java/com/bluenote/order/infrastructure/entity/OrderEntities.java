package com.bluenote.order.infrastructure.entity;

import java.time.LocalDateTime;

public final class OrderEntities {

    private OrderEntities() {
    }

    public static class CouponTemplate {
        private Long templateId;
        private String templateName;
        private Integer faceValue;
        private Integer thresholdAmount;
        private Integer validDays;
        private String coverUrl;
        private String description;
        private String templateStatus;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public Integer getFaceValue() { return faceValue; }
        public void setFaceValue(Integer faceValue) { this.faceValue = faceValue; }
        public Integer getThresholdAmount() { return thresholdAmount; }
        public void setThresholdAmount(Integer thresholdAmount) { this.thresholdAmount = thresholdAmount; }
        public Integer getValidDays() { return validDays; }
        public void setValidDays(Integer validDays) { this.validDays = validDays; }
        public String getCoverUrl() { return coverUrl; }
        public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getTemplateStatus() { return templateStatus; }
        public void setTemplateStatus(String templateStatus) { this.templateStatus = templateStatus; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class CouponActivity {
        private Long activityId;
        private Long templateId;
        private String activityName;
        private Integer totalStock;
        private Integer availableStock;
        private Integer lockedStock;
        private Integer soldStock;
        private Integer perUserLimit;
        private Integer payAmount;
        private String activityStatus;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private Integer payTimeoutMinutes;
        private LocalDateTime preheatedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Long version;

        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public String getActivityName() { return activityName; }
        public void setActivityName(String activityName) { this.activityName = activityName; }
        public Integer getTotalStock() { return totalStock; }
        public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }
        public Integer getAvailableStock() { return availableStock; }
        public void setAvailableStock(Integer availableStock) { this.availableStock = availableStock; }
        public Integer getLockedStock() { return lockedStock; }
        public void setLockedStock(Integer lockedStock) { this.lockedStock = lockedStock; }
        public Integer getSoldStock() { return soldStock; }
        public void setSoldStock(Integer soldStock) { this.soldStock = soldStock; }
        public Integer getPerUserLimit() { return perUserLimit; }
        public void setPerUserLimit(Integer perUserLimit) { this.perUserLimit = perUserLimit; }
        public Integer getPayAmount() { return payAmount; }
        public void setPayAmount(Integer payAmount) { this.payAmount = payAmount; }
        public String getActivityStatus() { return activityStatus; }
        public void setActivityStatus(String activityStatus) { this.activityStatus = activityStatus; }
        public LocalDateTime getStartAt() { return startAt; }
        public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
        public LocalDateTime getEndAt() { return endAt; }
        public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
        public Integer getPayTimeoutMinutes() { return payTimeoutMinutes; }
        public void setPayTimeoutMinutes(Integer payTimeoutMinutes) { this.payTimeoutMinutes = payTimeoutMinutes; }
        public LocalDateTime getPreheatedAt() { return preheatedAt; }
        public void setPreheatedAt(LocalDateTime preheatedAt) { this.preheatedAt = preheatedAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public Long getVersion() { return version; }
        public void setVersion(Long version) { this.version = version; }
    }

    public static class SeckillRequest {
        private Long requestId;
        private String clientRequestId;
        private Long userId;
        private Long activityId;
        private Long orderId;
        private Long userCouponId;
        private String requestStatus;
        private String resultMessage;
        private Integer payRequired;
        private Integer payAmount;
        private LocalDateTime expireAt;
        private LocalDateTime requestedAt;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getRequestId() { return requestId; }
        public void setRequestId(Long requestId) { this.requestId = requestId; }
        public String getClientRequestId() { return clientRequestId; }
        public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public Long getUserCouponId() { return userCouponId; }
        public void setUserCouponId(Long userCouponId) { this.userCouponId = userCouponId; }
        public String getRequestStatus() { return requestStatus; }
        public void setRequestStatus(String requestStatus) { this.requestStatus = requestStatus; }
        public String getResultMessage() { return resultMessage; }
        public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }
        public Integer getPayRequired() { return payRequired; }
        public void setPayRequired(Integer payRequired) { this.payRequired = payRequired; }
        public Integer getPayAmount() { return payAmount; }
        public void setPayAmount(Integer payAmount) { this.payAmount = payAmount; }
        public LocalDateTime getExpireAt() { return expireAt; }
        public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
        public LocalDateTime getRequestedAt() { return requestedAt; }
        public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class VoucherOrder {
        private Long orderId;
        private String orderNo;
        private Long requestId;
        private Long userId;
        private Long activityId;
        private Long templateId;
        private Integer payAmount;
        private String orderStatus;
        private LocalDateTime expireAt;
        private LocalDateTime paidAt;
        private LocalDateTime successAt;
        private LocalDateTime closedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Long version;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Long getRequestId() { return requestId; }
        public void setRequestId(Long requestId) { this.requestId = requestId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public Integer getPayAmount() { return payAmount; }
        public void setPayAmount(Integer payAmount) { this.payAmount = payAmount; }
        public String getOrderStatus() { return orderStatus; }
        public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
        public LocalDateTime getExpireAt() { return expireAt; }
        public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
        public LocalDateTime getPaidAt() { return paidAt; }
        public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
        public LocalDateTime getSuccessAt() { return successAt; }
        public void setSuccessAt(LocalDateTime successAt) { this.successAt = successAt; }
        public LocalDateTime getClosedAt() { return closedAt; }
        public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public Long getVersion() { return version; }
        public void setVersion(Long version) { this.version = version; }
    }

    public static class PaymentRecord {
        private Long paymentId;
        private Long orderId;
        private String orderNo;
        private String channel;
        private String channelTradeNo;
        private Integer payAmount;
        private String paymentStatus;
        private LocalDateTime paidAt;
        private String rawPayloadJson;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getPaymentId() { return paymentId; }
        public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getChannelTradeNo() { return channelTradeNo; }
        public void setChannelTradeNo(String channelTradeNo) { this.channelTradeNo = channelTradeNo; }
        public Integer getPayAmount() { return payAmount; }
        public void setPayAmount(Integer payAmount) { this.payAmount = payAmount; }
        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
        public LocalDateTime getPaidAt() { return paidAt; }
        public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
        public String getRawPayloadJson() { return rawPayloadJson; }
        public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class UserCoupon {
        private Long userCouponId;
        private Long userId;
        private Long templateId;
        private Long activityId;
        private Long sourceOrderId;
        private String couponStatus;
        private Integer faceValue;
        private Integer thresholdAmount;
        private LocalDateTime validStartAt;
        private LocalDateTime validEndAt;
        private LocalDateTime issuedAt;
        private LocalDateTime usedAt;
        private LocalDateTime expiredAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String templateName;
        private String activityName;

        public Long getUserCouponId() { return userCouponId; }
        public void setUserCouponId(Long userCouponId) { this.userCouponId = userCouponId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public Long getSourceOrderId() { return sourceOrderId; }
        public void setSourceOrderId(Long sourceOrderId) { this.sourceOrderId = sourceOrderId; }
        public String getCouponStatus() { return couponStatus; }
        public void setCouponStatus(String couponStatus) { this.couponStatus = couponStatus; }
        public Integer getFaceValue() { return faceValue; }
        public void setFaceValue(Integer faceValue) { this.faceValue = faceValue; }
        public Integer getThresholdAmount() { return thresholdAmount; }
        public void setThresholdAmount(Integer thresholdAmount) { this.thresholdAmount = thresholdAmount; }
        public LocalDateTime getValidStartAt() { return validStartAt; }
        public void setValidStartAt(LocalDateTime validStartAt) { this.validStartAt = validStartAt; }
        public LocalDateTime getValidEndAt() { return validEndAt; }
        public void setValidEndAt(LocalDateTime validEndAt) { this.validEndAt = validEndAt; }
        public LocalDateTime getIssuedAt() { return issuedAt; }
        public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
        public LocalDateTime getUsedAt() { return usedAt; }
        public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
        public LocalDateTime getExpiredAt() { return expiredAt; }
        public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getActivityName() { return activityName; }
        public void setActivityName(String activityName) { this.activityName = activityName; }
    }

    public static class OrderTimeoutTask {
        private Long taskId;
        private Long orderId;
        private Long activityId;
        private Long userId;
        private LocalDateTime expireAt;
        private String taskStatus;
        private LocalDateTime executedAt;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public LocalDateTime getExpireAt() { return expireAt; }
        public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
        public String getTaskStatus() { return taskStatus; }
        public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
        public LocalDateTime getExecutedAt() { return executedAt; }
        public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class OrderConsumeRecord {
        private Long id;
        private String consumerGroup;
        private String eventId;
        private String topic;
        private String eventType;
        private String bizKey;
        private String envelopeJson;
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
        public String getEnvelopeJson() { return envelopeJson; }
        public void setEnvelopeJson(String envelopeJson) { this.envelopeJson = envelopeJson; }
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

    public static class OrderOutboxEvent {
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
}
