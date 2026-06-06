package com.bluenote.member.user.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.member.user.api.dto.BatchUserSummaryRequest;
import com.bluenote.member.user.api.dto.BatchUserSummaryResponse;
import com.bluenote.member.user.api.dto.RegisterProfileRequest;
import com.bluenote.member.user.api.dto.RegisterProfileResponse;
import com.bluenote.member.user.api.dto.StatusCheckItem;
import com.bluenote.member.user.api.dto.StatusCheckRequest;
import com.bluenote.member.user.api.dto.StatusCheckResponse;
import com.bluenote.member.user.api.dto.UpdateProfileRequest;
import com.bluenote.member.user.api.dto.UpdateProfileResponse;
import com.bluenote.member.user.api.dto.UserCountsResponse;
import com.bluenote.member.user.api.dto.UserHomeResponse;
import com.bluenote.member.user.api.dto.UserProfileResponse;
import com.bluenote.member.user.api.dto.UserRelationResponse;
import com.bluenote.member.user.api.dto.UserSummaryItem;
import com.bluenote.member.user.api.dto.UserSummaryResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class UserApplicationService {

    private final ConcurrentMap<String, UserProfileResponse> profiles = new ConcurrentHashMap<>();

    public UserApplicationService() {
        profiles.put("10001", defaultProfile("10001", "小蓝"));
    }

    public UserProfileResponse currentUserProfile(String userId) {
        return profiles.computeIfAbsent(userId, ignored -> defaultProfile(userId, "小蓝"));
    }

    public UpdateProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        UserProfileResponse current = currentUserProfile(userId);
        long baseVersion = request.baseProfileVersion() == null ? current.profileVersion() : request.baseProfileVersion();
        if (baseVersion != current.profileVersion()) {
            throw new BusinessException(ApiErrorCode.PROFILE_VERSION_CONFLICT);
        }
        UserProfileResponse updated = new UserProfileResponse(
                current.userId(),
                current.bluenoteNo(),
                valueOrDefault(request.nickname(), current.nickname()),
                valueOrDefault(request.avatarFileId(), current.avatarFileId()),
                current.avatarUrl(),
                valueOrDefault(request.bio(), current.bio()),
                valueOrDefault(request.gender(), current.gender()),
                valueOrDefault(request.birthday(), current.birthday()),
                valueOrDefault(request.regionCode(), current.regionCode()),
                valueOrDefault(request.homeCoverFileId(), current.homeCoverFileId()),
                current.homeCoverUrl(),
                current.userStatus(),
                current.profileVersion() + 1
        );
        profiles.put(userId, updated);
        return new UpdateProfileResponse(userId, updated.profileVersion(), now());
    }

    public UserSummaryResponse publicProfile(String userId) {
        UserProfileResponse profile = profiles.get(userId);
        if (profile == null) {
            throw new BusinessException(ApiErrorCode.USER_NOT_FOUND);
        }
        return toSummary(profile);
    }

    public UserHomeResponse home(String userId) {
        return new UserHomeResponse(
                publicProfile(userId),
                new UserCountsResponse(0, 0, 0, 0),
                new UserRelationResponse("UNKNOWN"),
                false
        );
    }

    public RegisterProfileResponse registerProfile(RegisterProfileRequest request) {
        UserProfileResponse profile = profiles.computeIfAbsent(request.userId(), ignored -> defaultProfile(request.userId(), request.defaultNickname()));
        return new RegisterProfileResponse(
                profile.userId(),
                profile.bluenoteNo(),
                profile.nickname(),
                profile.userStatus(),
                profile.profileVersion()
        );
    }

    public BatchUserSummaryResponse batchSummary(BatchUserSummaryRequest request) {
        List<UserSummaryItem> users = request.userIds().stream()
                .map(this::summaryItem)
                .toList();
        return new BatchUserSummaryResponse(users);
    }

    public StatusCheckResponse statusCheck(StatusCheckRequest request) {
        List<StatusCheckItem> results = request.userIds().stream()
                .map(userId -> {
                    UserProfileResponse profile = profiles.get(userId);
                    if (profile == null) {
                        return new StatusCheckItem(userId, false, null, false, "USER_NOT_FOUND");
                    }
                    boolean allowed = "NORMAL".equals(profile.userStatus());
                    return new StatusCheckItem(userId, true, profile.userStatus(), allowed, allowed ? null : "USER_DISABLED");
                })
                .toList();
        return new StatusCheckResponse(results);
    }

    private UserSummaryItem summaryItem(String userId) {
        UserProfileResponse profile = profiles.get(userId);
        if (profile == null) {
            return new UserSummaryItem(userId, null, null, null, null, null, "NOT_FOUND");
        }
        return new UserSummaryItem(
                profile.userId(),
                profile.nickname(),
                profile.avatarUrl(),
                profile.bluenoteNo(),
                profile.userStatus(),
                profile.profileVersion(),
                "FOUND"
        );
    }

    private UserSummaryResponse toSummary(UserProfileResponse profile) {
        return new UserSummaryResponse(
                profile.userId(),
                profile.bluenoteNo(),
                profile.nickname(),
                profile.avatarUrl(),
                profile.bio(),
                profile.userStatus(),
                profile.profileVersion()
        );
    }

    private UserProfileResponse defaultProfile(String userId, String nickname) {
        return new UserProfileResponse(
                userId,
                "BN" + userId,
                nickname,
                null,
                null,
                null,
                "UNKNOWN",
                null,
                null,
                null,
                null,
                "NORMAL",
                1
        );
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.ofHours(8)).toString();
    }
}
