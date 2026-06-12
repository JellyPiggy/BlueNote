package com.bluenote.social.push.infrastructure.entity;

import java.time.LocalDateTime;

public class PushPreferenceEntity {

    private Long userId;
    private Integer globalEnabled;
    private Integer interactionEnabled;
    private Integer followEnabled;
    private Integer systemEnabled;
    private Integer orderEnabled;
    private Integer imEnabled;
    private Integer showImDetail;
    private Integer quietHoursEnabled;
    private String quietStart;
    private String quietEnd;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getGlobalEnabled() {
        return globalEnabled;
    }

    public void setGlobalEnabled(Integer globalEnabled) {
        this.globalEnabled = globalEnabled;
    }

    public Integer getInteractionEnabled() {
        return interactionEnabled;
    }

    public void setInteractionEnabled(Integer interactionEnabled) {
        this.interactionEnabled = interactionEnabled;
    }

    public Integer getFollowEnabled() {
        return followEnabled;
    }

    public void setFollowEnabled(Integer followEnabled) {
        this.followEnabled = followEnabled;
    }

    public Integer getSystemEnabled() {
        return systemEnabled;
    }

    public void setSystemEnabled(Integer systemEnabled) {
        this.systemEnabled = systemEnabled;
    }

    public Integer getOrderEnabled() {
        return orderEnabled;
    }

    public void setOrderEnabled(Integer orderEnabled) {
        this.orderEnabled = orderEnabled;
    }

    public Integer getImEnabled() {
        return imEnabled;
    }

    public void setImEnabled(Integer imEnabled) {
        this.imEnabled = imEnabled;
    }

    public Integer getShowImDetail() {
        return showImDetail;
    }

    public void setShowImDetail(Integer showImDetail) {
        this.showImDetail = showImDetail;
    }

    public Integer getQuietHoursEnabled() {
        return quietHoursEnabled;
    }

    public void setQuietHoursEnabled(Integer quietHoursEnabled) {
        this.quietHoursEnabled = quietHoursEnabled;
    }

    public String getQuietStart() {
        return quietStart;
    }

    public void setQuietStart(String quietStart) {
        this.quietStart = quietStart;
    }

    public String getQuietEnd() {
        return quietEnd;
    }

    public void setQuietEnd(String quietEnd) {
        this.quietEnd = quietEnd;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
