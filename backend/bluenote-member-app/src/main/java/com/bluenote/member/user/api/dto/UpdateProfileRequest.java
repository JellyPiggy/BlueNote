package com.bluenote.member.user.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @Size(min = 1, max = 64)
    private String nickname;
    private boolean nicknamePresent;

    private String avatarFileId;
    private boolean avatarFileIdPresent;

    @Size(max = 256)
    private String bio;
    private boolean bioPresent;

    @Pattern(regexp = "^(UNKNOWN|MALE|FEMALE)$")
    private String gender;
    private boolean genderPresent;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
    private String birthday;
    private boolean birthdayPresent;

    @Size(max = 32)
    private String regionCode;
    private boolean regionCodePresent;

    private String homeCoverFileId;
    private boolean homeCoverFileIdPresent;

    private Long baseProfileVersion;

    public String nickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.nicknamePresent = true;
    }

    @JsonIgnore
    public boolean nicknamePresent() {
        return nicknamePresent;
    }

    public String avatarFileId() {
        return avatarFileId;
    }

    public void setAvatarFileId(String avatarFileId) {
        this.avatarFileId = avatarFileId;
        this.avatarFileIdPresent = true;
    }

    @JsonIgnore
    public boolean avatarFileIdPresent() {
        return avatarFileIdPresent;
    }

    public String bio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
        this.bioPresent = true;
    }

    @JsonIgnore
    public boolean bioPresent() {
        return bioPresent;
    }

    public String gender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
        this.genderPresent = true;
    }

    @JsonIgnore
    public boolean genderPresent() {
        return genderPresent;
    }

    public String birthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
        this.birthdayPresent = true;
    }

    @JsonIgnore
    public boolean birthdayPresent() {
        return birthdayPresent;
    }

    public String regionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
        this.regionCodePresent = true;
    }

    @JsonIgnore
    public boolean regionCodePresent() {
        return regionCodePresent;
    }

    public String homeCoverFileId() {
        return homeCoverFileId;
    }

    public void setHomeCoverFileId(String homeCoverFileId) {
        this.homeCoverFileId = homeCoverFileId;
        this.homeCoverFileIdPresent = true;
    }

    @JsonIgnore
    public boolean homeCoverFileIdPresent() {
        return homeCoverFileIdPresent;
    }

    public Long baseProfileVersion() {
        return baseProfileVersion;
    }

    public void setBaseProfileVersion(Long baseProfileVersion) {
        this.baseProfileVersion = baseProfileVersion;
    }
}
