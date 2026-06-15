package com.bluenote.member.user.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.member.common.JsonPayloads;
import com.bluenote.member.common.MemberIdGenerator;
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
import com.bluenote.member.user.infrastructure.entity.UserOutboxEventEntity;
import com.bluenote.member.user.infrastructure.entity.UserProfileAuditEntity;
import com.bluenote.member.user.infrastructure.entity.UserProfileEntity;
import com.bluenote.member.user.infrastructure.client.UserCounterClient;
import com.bluenote.member.user.infrastructure.client.UserCounterClient.UserCounterResult;
import com.bluenote.member.user.infrastructure.client.UserFileClient;
import com.bluenote.member.user.infrastructure.client.UserFileClient.ValidatedUserFile;
import com.bluenote.member.user.infrastructure.client.UserRelationClient;
import com.bluenote.member.user.infrastructure.mapper.UserOutboxEventMapper;
import com.bluenote.member.user.infrastructure.mapper.UserProfileAuditMapper;
import com.bluenote.member.user.infrastructure.mapper.UserProfileMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserApplicationService {

    private static final String DEFAULT_NICKNAME = "小蓝";
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private final UserProfileMapper profileMapper;
    private final UserProfileAuditMapper profileAuditMapper;
    private final UserOutboxEventMapper outboxEventMapper;
    private final MemberIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;
    private final UserCounterClient userCounterClient;
    private final UserFileClient userFileClient;
    private final UserRelationClient userRelationClient;

    public UserApplicationService(
            UserProfileMapper profileMapper,
            UserProfileAuditMapper profileAuditMapper,
            UserOutboxEventMapper outboxEventMapper,
            MemberIdGenerator idGenerator,
            JsonPayloads jsonPayloads,
            UserCounterClient userCounterClient,
            UserFileClient userFileClient,
            UserRelationClient userRelationClient
    ) {
        this.profileMapper = profileMapper;
        this.profileAuditMapper = profileAuditMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
        this.userCounterClient = userCounterClient;
        this.userFileClient = userFileClient;
        this.userRelationClient = userRelationClient;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse currentUserProfile(String userId) {
        return toProfileResponse(requireProfile(parseUserId(userId)));
    }

    @Transactional
    public UpdateProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        Long parsedUserId = parseUserId(userId);
        UserProfileEntity current = requireProfile(parsedUserId);
        long baseVersion = request.baseProfileVersion() == null
                ? current.getProfileVersion()
                : request.baseProfileVersion();
        if (baseVersion != current.getProfileVersion()) {
            throw new BusinessException(ApiErrorCode.PROFILE_VERSION_CONFLICT);
        }

        UserProfileEntity updated = copy(current);
        List<FieldChange> changes = new ArrayList<>();
        ProfileFileBindings fileBindings = applyProfileChanges(userId, request, current, updated, changes);
        if (changes.isEmpty()) {
            return new UpdateProfileResponse(userId, current.getProfileVersion(), toOffsetString(current.getUpdatedAt()));
        }

        LocalDateTime now = now();
        updated.setProfileVersion(current.getProfileVersion() + 1);
        updated.setUpdatedAt(now);
        int updatedRows = profileMapper.updateByVersion(updated, current.getProfileVersion());
        if (updatedRows == 0) {
            throw new BusinessException(ApiErrorCode.PROFILE_VERSION_CONFLICT);
        }

        for (FieldChange change : changes) {
            insertAudit(parsedUserId, change, now);
        }
        insertUserOutbox(updated, now);
        bindProfileFiles(userId, fileBindings);
        return new UpdateProfileResponse(userId, updated.getProfileVersion(), toOffsetString(now));
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse publicProfile(String userId) {
        return toSummary(requireProfile(parseUserId(userId)));
    }

    @Transactional(readOnly = true)
    public UserHomeResponse home(String viewerId, String userId) {
        UserSummaryResponse user = publicProfile(userId);
        UserCountsResponse counts = new UserCountsResponse(0, 0, 0, 0);
        UserRelationResponse relation = new UserRelationResponse("UNKNOWN");
        boolean degraded = false;

        try {
            UserCounterResult result = userCounterClient.userCounts(userId);
            counts = result.counts();
            degraded = result.degraded();
        } catch (RuntimeException exception) {
            degraded = true;
        }

        if (!isBlank(viewerId)) {
            try {
                relation = new UserRelationResponse(userRelationClient.followStatus(viewerId, userId));
            } catch (RuntimeException exception) {
                degraded = true;
            }
        }

        return new UserHomeResponse(user, counts, relation, degraded);
    }

    @Transactional
    public RegisterProfileResponse registerProfile(RegisterProfileRequest request) {
        Long userId = parseUserId(request.userId());
        UserProfileEntity existing = profileMapper.selectByUserId(userId);
        if (existing != null) {
            return toRegisterProfileResponse(existing);
        }

        LocalDateTime now = now();
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setBluenoteNo("BN" + userId);
        entity.setNickname(defaultNickname(request.defaultNickname()));
        entity.setGender("UNKNOWN");
        entity.setUserStatus("NORMAL");
        entity.setProfileVersion(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);

        try {
            profileMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            UserProfileEntity duplicate = profileMapper.selectByUserId(userId);
            if (duplicate != null) {
                return toRegisterProfileResponse(duplicate);
            }
            throw new BusinessException(ApiErrorCode.DATA_CONFLICT);
        }
        return toRegisterProfileResponse(entity);
    }

    @Transactional(readOnly = true)
    public BatchUserSummaryResponse batchSummary(BatchUserSummaryRequest request) {
        List<Long> userIds = request.userIds().stream()
                .map(this::parseUserId)
                .toList();
        Map<String, UserProfileEntity> profiles = profileMapper.selectByUserIds(userIds).stream()
                .collect(Collectors.toMap(profile -> String.valueOf(profile.getUserId()), Function.identity()));
        List<UserSummaryItem> users = request.userIds().stream()
                .map(userId -> summaryItem(userId, profiles.get(userId)))
                .toList();
        return new BatchUserSummaryResponse(users);
    }

    @Transactional(readOnly = true)
    public StatusCheckResponse statusCheck(StatusCheckRequest request) {
        List<Long> userIds = request.userIds().stream()
                .map(this::parseUserId)
                .toList();
        Map<String, UserProfileEntity> profiles = profileMapper.selectByUserIds(userIds).stream()
                .collect(Collectors.toMap(profile -> String.valueOf(profile.getUserId()), Function.identity()));
        List<StatusCheckItem> results = request.userIds().stream()
                .map(userId -> statusCheckItem(userId, profiles.get(userId)))
                .toList();
        return new StatusCheckResponse(results);
    }

    private ProfileFileBindings applyProfileChanges(
            String userId,
            UpdateProfileRequest request,
            UserProfileEntity current,
            UserProfileEntity updated,
            List<FieldChange> changes
    ) {
        String avatarToBind = null;
        String homeCoverToBind = null;
        if (request.nicknamePresent()) {
            String nickname = request.nickname();
            if (nickname == null || nickname.isBlank()) {
                throw new BusinessException(ApiErrorCode.NICKNAME_INVALID);
            }
            setString("nickname", current.getNickname(), nickname, updated::setNickname, changes);
        }
        if (request.avatarFileIdPresent()) {
            Long avatarFileId = parseOptionalId(request.avatarFileId(), ApiErrorCode.AVATAR_FILE_INVALID);
            setLong("avatarFileId", current.getAvatarFileId(), avatarFileId, updated::setAvatarFileId, changes);
            if (avatarFileId != null && (!Objects.equals(current.getAvatarFileId(), avatarFileId) || isBlank(current.getAvatarUrl()))) {
                ValidatedUserFile file = userFileClient.validateAvatar(userId, String.valueOf(avatarFileId));
                setString("avatarUrl", current.getAvatarUrl(), file.accessUrl(), updated::setAvatarUrl, changes);
                avatarToBind = file.fileId();
            } else if (!Objects.equals(current.getAvatarFileId(), avatarFileId)) {
                if (avatarFileId == null) {
                    setString("avatarUrl", current.getAvatarUrl(), null, updated::setAvatarUrl, changes);
                }
            }
        }
        if (request.bioPresent()) {
            setString("bio", current.getBio(), request.bio(), updated::setBio, changes);
        }
        if (request.genderPresent()) {
            String gender = request.gender() == null ? "UNKNOWN" : request.gender();
            setString("gender", current.getGender(), gender, updated::setGender, changes);
        }
        if (request.birthdayPresent()) {
            LocalDate birthday = parseBirthday(request.birthday());
            setDate("birthday", current.getBirthday(), birthday, updated::setBirthday, changes);
        }
        if (request.regionCodePresent()) {
            setString("regionCode", current.getRegionCode(), request.regionCode(), updated::setRegionCode, changes);
        }
        if (request.homeCoverFileIdPresent()) {
            Long homeCoverFileId = parseOptionalId(request.homeCoverFileId(), ApiErrorCode.HOME_COVER_FILE_INVALID);
            setLong("homeCoverFileId", current.getHomeCoverFileId(), homeCoverFileId, updated::setHomeCoverFileId, changes);
            if (homeCoverFileId != null
                    && (!Objects.equals(current.getHomeCoverFileId(), homeCoverFileId) || isBlank(current.getHomeCoverUrl()))) {
                ValidatedUserFile file = userFileClient.validateHomeCover(userId, String.valueOf(homeCoverFileId));
                setString("homeCoverUrl", current.getHomeCoverUrl(), file.accessUrl(), updated::setHomeCoverUrl, changes);
                homeCoverToBind = file.fileId();
            } else if (!Objects.equals(current.getHomeCoverFileId(), homeCoverFileId)) {
                if (homeCoverFileId == null) {
                    setString("homeCoverUrl", current.getHomeCoverUrl(), null, updated::setHomeCoverUrl, changes);
                }
            }
        }
        return new ProfileFileBindings(avatarToBind, homeCoverToBind);
    }

    private void bindProfileFiles(String userId, ProfileFileBindings fileBindings) {
        if (fileBindings.avatarFileId() != null) {
            userFileClient.bindAvatar(userId, fileBindings.avatarFileId());
        }
        if (fileBindings.homeCoverFileId() != null) {
            userFileClient.bindHomeCover(userId, fileBindings.homeCoverFileId());
        }
    }

    private void setString(
            String fieldName,
            String oldValue,
            String newValue,
            java.util.function.Consumer<String> setter,
            List<FieldChange> changes
    ) {
        if (!Objects.equals(oldValue, newValue)) {
            setter.accept(newValue);
            changes.add(new FieldChange(fieldName, oldValue, newValue));
        }
    }

    private void setLong(
            String fieldName,
            Long oldValue,
            Long newValue,
            java.util.function.Consumer<Long> setter,
            List<FieldChange> changes
    ) {
        if (!Objects.equals(oldValue, newValue)) {
            setter.accept(newValue);
            changes.add(new FieldChange(fieldName, stringValue(oldValue), stringValue(newValue)));
        }
    }

    private void setDate(
            String fieldName,
            LocalDate oldValue,
            LocalDate newValue,
            java.util.function.Consumer<LocalDate> setter,
            List<FieldChange> changes
    ) {
        if (!Objects.equals(oldValue, newValue)) {
            setter.accept(newValue);
            changes.add(new FieldChange(fieldName, stringValue(oldValue), stringValue(newValue)));
        }
    }

    private void insertAudit(Long userId, FieldChange change, LocalDateTime now) {
        UserProfileAuditEntity entity = new UserProfileAuditEntity();
        entity.setId(idGenerator.nextId());
        entity.setUserId(userId);
        entity.setFieldName(change.fieldName());
        entity.setOldValueMask(change.oldValue());
        entity.setNewValueMask(change.newValue());
        entity.setOperatorId(userId);
        entity.setOperatorType("USER");
        entity.setTraceId(TraceIdHolder.currentOrNew());
        entity.setCreatedAt(now);
        profileAuditMapper.insert(entity);
    }

    private void insertUserOutbox(UserProfileEntity profile, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        UserOutboxEventEntity entity = new UserOutboxEventEntity();
        entity.setEventId(eventId);
        entity.setEventType("UserProfileUpdated");
        entity.setAggregateId(profile.getUserId());
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(
                eventId,
                "UserProfileUpdated",
                profile.getUserId(),
                userProfileUpdatedPayload(profile)
        )));
        entity.setSendStatus("INIT");
        entity.setRetryCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        outboxEventMapper.insert(entity);
    }

    private Map<String, Object> eventEnvelope(
            String eventId,
            String eventType,
            Long aggregateId,
            Map<String, Object> payload
    ) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", OffsetDateTime.now(CHINA_ZONE).toString());
        envelope.put("traceId", TraceIdHolder.currentOrNew());
        envelope.put("producer", "bluenote-user");
        envelope.put("bizKey", String.valueOf(aggregateId));
        envelope.put("payload", payload);
        return envelope;
    }

    private Map<String, Object> userProfileUpdatedPayload(UserProfileEntity profile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", String.valueOf(profile.getUserId()));
        payload.put("bluenoteNo", profile.getBluenoteNo());
        payload.put("nickname", profile.getNickname());
        payload.put("avatarFileId", stringValue(profile.getAvatarFileId()));
        payload.put("avatarUrl", profile.getAvatarUrl());
        payload.put("bio", profile.getBio());
        payload.put("userStatus", profile.getUserStatus());
        payload.put("profileVersion", profile.getProfileVersion());
        return payload;
    }

    private UserProfileEntity requireProfile(Long userId) {
        UserProfileEntity profile = profileMapper.selectByUserId(userId);
        if (profile == null) {
            throw new BusinessException(ApiErrorCode.USER_NOT_FOUND);
        }
        return profile;
    }

    private UserProfileResponse toProfileResponse(UserProfileEntity profile) {
        return new UserProfileResponse(
                String.valueOf(profile.getUserId()),
                profile.getBluenoteNo(),
                profile.getNickname(),
                stringValue(profile.getAvatarFileId()),
                profile.getAvatarUrl(),
                profile.getBio(),
                profile.getGender(),
                stringValue(profile.getBirthday()),
                profile.getRegionCode(),
                stringValue(profile.getHomeCoverFileId()),
                profile.getHomeCoverUrl(),
                profile.getUserStatus(),
                profile.getProfileVersion()
        );
    }

    private RegisterProfileResponse toRegisterProfileResponse(UserProfileEntity profile) {
        return new RegisterProfileResponse(
                String.valueOf(profile.getUserId()),
                profile.getBluenoteNo(),
                profile.getNickname(),
                profile.getUserStatus(),
                profile.getProfileVersion()
        );
    }

    private UserSummaryResponse toSummary(UserProfileEntity profile) {
        return new UserSummaryResponse(
                String.valueOf(profile.getUserId()),
                profile.getBluenoteNo(),
                profile.getNickname(),
                profile.getAvatarUrl(),
                profile.getBio(),
                profile.getUserStatus(),
                profile.getProfileVersion()
        );
    }

    private UserSummaryItem summaryItem(String userId, UserProfileEntity profile) {
        if (profile == null) {
            return new UserSummaryItem(userId, null, null, null, null, null, "NOT_FOUND");
        }
        return new UserSummaryItem(
                String.valueOf(profile.getUserId()),
                profile.getNickname(),
                profile.getAvatarUrl(),
                profile.getBluenoteNo(),
                profile.getUserStatus(),
                profile.getProfileVersion(),
                "FOUND"
        );
    }

    private StatusCheckItem statusCheckItem(String userId, UserProfileEntity profile) {
        if (profile == null) {
            return new StatusCheckItem(userId, false, null, false, "USER_NOT_FOUND");
        }
        boolean allowed = "NORMAL".equals(profile.getUserStatus());
        return new StatusCheckItem(
                userId,
                true,
                profile.getUserStatus(),
                allowed,
                allowed ? null : "USER_DISABLED"
        );
    }

    private UserProfileEntity copy(UserProfileEntity source) {
        UserProfileEntity target = new UserProfileEntity();
        target.setUserId(source.getUserId());
        target.setBluenoteNo(source.getBluenoteNo());
        target.setNickname(source.getNickname());
        target.setAvatarFileId(source.getAvatarFileId());
        target.setAvatarUrl(source.getAvatarUrl());
        target.setBio(source.getBio());
        target.setGender(source.getGender());
        target.setBirthday(source.getBirthday());
        target.setRegionCode(source.getRegionCode());
        target.setHomeCoverFileId(source.getHomeCoverFileId());
        target.setHomeCoverUrl(source.getHomeCoverUrl());
        target.setUserStatus(source.getUserStatus());
        target.setProfileVersion(source.getProfileVersion());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setDeleted(source.getDeleted());
        return target;
    }

    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ApiErrorCode.PARAM_INVALID);
        }
    }

    private Long parseOptionalId(String value, ApiErrorCode errorCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private LocalDate parseBirthday(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ApiErrorCode.BIRTHDAY_INVALID);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CHINA_ZONE);
    }

    private String toOffsetString(LocalDateTime dateTime) {
        return dateTime.atZone(CHINA_ZONE).toOffsetDateTime().toString();
    }

    private String defaultNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        return nickname;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record FieldChange(String fieldName, String oldValue, String newValue) {
    }

    private record ProfileFileBindings(String avatarFileId, String homeCoverFileId) {
    }
}
