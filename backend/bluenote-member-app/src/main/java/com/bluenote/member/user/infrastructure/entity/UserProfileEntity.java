package com.bluenote.member.user.infrastructure.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class UserProfileEntity {

    private Long userId;
    private String bluenoteNo;
    private String nickname;
    private Long avatarFileId;
    private String avatarUrl;
    private String bio;
    private String gender;
    private LocalDate birthday;
    private String regionCode;
    private Long homeCoverFileId;
    private String homeCoverUrl;
    private String userStatus;
    private Long profileVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getBluenoteNo() {
        return bluenoteNo;
    }

    public void setBluenoteNo(String bluenoteNo) {
        this.bluenoteNo = bluenoteNo;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Long getAvatarFileId() {
        return avatarFileId;
    }

    public void setAvatarFileId(Long avatarFileId) {
        this.avatarFileId = avatarFileId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public Long getHomeCoverFileId() {
        return homeCoverFileId;
    }

    public void setHomeCoverFileId(Long homeCoverFileId) {
        this.homeCoverFileId = homeCoverFileId;
    }

    public String getHomeCoverUrl() {
        return homeCoverUrl;
    }

    public void setHomeCoverUrl(String homeCoverUrl) {
        this.homeCoverUrl = homeCoverUrl;
    }

    public String getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(String userStatus) {
        this.userStatus = userStatus;
    }

    public Long getProfileVersion() {
        return profileVersion;
    }

    public void setProfileVersion(Long profileVersion) {
        this.profileVersion = profileVersion;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
