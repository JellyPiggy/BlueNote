package com.bluenote.content.file.application;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import com.bluenote.content.common.ContentIdGenerator;
import com.bluenote.content.common.JsonPayloads;
import com.bluenote.content.config.ContentStorageConfiguration.MinioStorageProperties;
import com.bluenote.content.file.api.dto.BatchValidatedFileResponse;
import com.bluenote.content.file.api.dto.ConfirmUploadRequest;
import com.bluenote.content.file.api.dto.ConfirmUploadResponse;
import com.bluenote.content.file.api.dto.CreateUploadTokenRequest;
import com.bluenote.content.file.api.dto.FileAccessUrlResponse;
import com.bluenote.content.file.api.dto.InternalBatchBindFileRequest;
import com.bluenote.content.file.api.dto.InternalBatchBindFileResponse;
import com.bluenote.content.file.api.dto.InternalBatchValidateFileRequest;
import com.bluenote.content.file.api.dto.InternalBatchValidateFileResponse;
import com.bluenote.content.file.api.dto.InternalBindFileRequest;
import com.bluenote.content.file.api.dto.InternalBindFileResponse;
import com.bluenote.content.file.api.dto.InternalValidateFileRequest;
import com.bluenote.content.file.api.dto.InternalValidateFileResponse;
import com.bluenote.content.file.api.dto.UploadTokenResponse;
import com.bluenote.content.file.api.dto.ValidatedFileResponse;
import com.bluenote.content.file.infrastructure.entity.FileBindingEntity;
import com.bluenote.content.file.infrastructure.entity.FileObjectEntity;
import com.bluenote.content.file.infrastructure.entity.FileOutboxEventEntity;
import com.bluenote.content.file.infrastructure.entity.FileUploadSessionEntity;
import com.bluenote.content.file.infrastructure.mapper.FileBindingMapper;
import com.bluenote.content.file.infrastructure.mapper.FileObjectMapper;
import com.bluenote.content.file.infrastructure.mapper.FileOutboxEventMapper;
import com.bluenote.content.file.infrastructure.mapper.FileUploadSessionMapper;
import com.bluenote.content.file.infrastructure.storage.ObjectStorageClient;
import com.bluenote.content.file.infrastructure.storage.ObjectStorageClient.PresignedUpload;
import com.bluenote.content.file.infrastructure.storage.ObjectStorageClient.StoredObjectInfo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileApplicationService {

    private static final String STORAGE_TYPE_MINIO = "MINIO";
    private static final String STATUS_INIT = "INIT";
    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_BOUND = "BOUND";
    private static final String BIND_STATUS_BOUND = "BOUND";
    private static final String UPLOAD_METHOD_PRESIGNED_PUT = "PRESIGNED_PUT";
    private static final String AUDIT_STATUS_SKIPPED = "SKIPPED";
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> USABLE_STATUSES = List.of(STATUS_UPLOADED, STATUS_BOUND);
    private static final Map<String, FileSceneSpec> SCENE_SPECS = Map.of(
            "USER_AVATAR", new FileSceneSpec(
                    "USER_AVATAR",
                    Set.of("image/jpeg", "image/png", "image/webp"),
                    5 * 1024 * 1024L,
                    "PUBLIC",
                    "user-avatar"
            ),
            "USER_HOME_COVER", new FileSceneSpec(
                    "USER_HOME_COVER",
                    Set.of("image/jpeg", "image/png", "image/webp"),
                    10 * 1024 * 1024L,
                    "PUBLIC",
                    "user-home-cover"
            ),
            "NOTE_IMAGE", new FileSceneSpec(
                    "NOTE_IMAGE",
                    Set.of("image/jpeg", "image/png", "image/webp"),
                    10 * 1024 * 1024L,
                    "PUBLIC",
                    "note-image"
            )
    );

    private final FileObjectMapper fileObjectMapper;
    private final FileUploadSessionMapper uploadSessionMapper;
    private final FileBindingMapper fileBindingMapper;
    private final FileOutboxEventMapper outboxEventMapper;
    private final ObjectStorageClient objectStorageClient;
    private final MinioStorageProperties storageProperties;
    private final ContentIdGenerator idGenerator;
    private final JsonPayloads jsonPayloads;

    public FileApplicationService(
            FileObjectMapper fileObjectMapper,
            FileUploadSessionMapper uploadSessionMapper,
            FileBindingMapper fileBindingMapper,
            FileOutboxEventMapper outboxEventMapper,
            ObjectStorageClient objectStorageClient,
            MinioStorageProperties storageProperties,
            ContentIdGenerator idGenerator,
            JsonPayloads jsonPayloads
    ) {
        this.fileObjectMapper = fileObjectMapper;
        this.uploadSessionMapper = uploadSessionMapper;
        this.fileBindingMapper = fileBindingMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.objectStorageClient = objectStorageClient;
        this.storageProperties = storageProperties;
        this.idGenerator = idGenerator;
        this.jsonPayloads = jsonPayloads;
    }

    @Transactional
    public UploadTokenResponse createUploadToken(String userId, CreateUploadTokenRequest request) {
        Long ownerId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        FileSceneSpec spec = requireSceneSpec(request.scene());
        String mimeType = normalizeMimeType(request.mimeType());
        validateMimeType(spec, mimeType);
        validateSize(spec, request.fileSize());

        long fileId = idGenerator.nextId();
        long uploadId = idGenerator.nextId();
        LocalDateTime now = now();
        LocalDateTime expireAt = now.plusSeconds(storageProperties.getUploadUrlExpiresSeconds());
        String fileExt = extensionFor(mimeType);
        String objectKey = objectKey(spec, ownerId, fileId, fileExt);

        PresignedUpload upload = objectStorageClient.createPresignedPutUrl(
                storageProperties.getBucket(),
                objectKey,
                mimeType,
                storageProperties.getUploadUrlExpiresSeconds()
        );

        FileObjectEntity file = new FileObjectEntity();
        file.setFileId(fileId);
        file.setOwnerId(ownerId);
        file.setScene(spec.scene());
        file.setStorageType(STORAGE_TYPE_MINIO);
        file.setBucket(storageProperties.getBucket());
        file.setObjectKey(objectKey);
        file.setOriginalFilename(request.filename());
        file.setFileExt(fileExt);
        file.setMimeType(mimeType);
        file.setFileSize(request.fileSize());
        file.setFileStatus(STATUS_INIT);
        file.setAuditStatus(AUDIT_STATUS_SKIPPED);
        file.setAccessLevel(spec.accessLevel());
        file.setCreatedAt(now);
        file.setUpdatedAt(now);
        file.setDeleted(0);
        fileObjectMapper.insert(file);

        FileUploadSessionEntity session = new FileUploadSessionEntity();
        session.setUploadId(uploadId);
        session.setFileId(fileId);
        session.setOwnerId(ownerId);
        session.setUploadMethod(UPLOAD_METHOD_PRESIGNED_PUT);
        session.setUploadStatus(STATUS_INIT);
        session.setExpectedSize(request.fileSize());
        session.setExpectedMimeType(mimeType);
        session.setUploadUrlExpireAt(expireAt);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        uploadSessionMapper.insert(session);

        return new UploadTokenResponse(
                String.valueOf(fileId),
                UPLOAD_METHOD_PRESIGNED_PUT,
                upload.uploadUrl(),
                upload.headers(),
                upload.expireAt().toString(),
                objectKey
        );
    }

    @Transactional
    public ConfirmUploadResponse confirmUpload(String userId, String fileId, ConfirmUploadRequest request) {
        Long ownerId = parseId(userId, ApiErrorCode.ACCESS_TOKEN_INVALID);
        FileObjectEntity file = requireFile(parseId(fileId, ApiErrorCode.FILE_NOT_FOUND));
        requireOwner(file, ownerId);

        if (STATUS_UPLOADED.equals(file.getFileStatus()) || STATUS_BOUND.equals(file.getFileStatus())) {
            return toConfirmResponse(file);
        }
        if (!STATUS_INIT.equals(file.getFileStatus())) {
            throw new BusinessException(ApiErrorCode.FILE_STATUS_INVALID);
        }

        FileUploadSessionEntity session = uploadSessionMapper.selectLatestByFileId(file.getFileId());
        if (session == null) {
            throw new BusinessException(ApiErrorCode.FILE_STATUS_INVALID);
        }

        LocalDateTime now = now();
        if (session.getUploadUrlExpireAt().isBefore(now)) {
            uploadSessionMapper.markExpired(session.getUploadId(), now);
            throw new BusinessException(ApiErrorCode.UPLOAD_TOKEN_EXPIRED);
        }

        StoredObjectInfo objectInfo = objectStorageClient.statObject(file.getBucket(), file.getObjectKey());
        if (objectInfo == null) {
            throw new BusinessException(ApiErrorCode.UPLOAD_NOT_COMPLETED);
        }
        validateConfirmedObject(file, request, objectInfo);

        fileObjectMapper.markUploaded(file.getFileId(), objectInfo.etag(), objectInfo.size(), now, now);
        uploadSessionMapper.markUploaded(session.getUploadId(), now, now);

        FileObjectEntity uploaded = requireFile(file.getFileId());
        insertFileOutbox("FileUploaded", uploaded, uploadedPayload(uploaded), now);
        return toConfirmResponse(uploaded);
    }

    @Transactional(readOnly = true)
    public FileAccessUrlResponse accessUrl(String viewerId, String fileId, Integer expireSeconds) {
        FileObjectEntity file = requireFile(parseId(fileId, ApiErrorCode.FILE_NOT_FOUND));
        if (!USABLE_STATUSES.contains(file.getFileStatus())) {
            throw new BusinessException(ApiErrorCode.FILE_STATUS_INVALID);
        }
        int expires = normalizeExpireSeconds(expireSeconds);
        if (!"PUBLIC".equals(file.getAccessLevel())) {
            Long parsedViewerId = parseId(viewerId, ApiErrorCode.ACCESS_TOKEN_INVALID);
            requireOwner(file, parsedViewerId);
        }
        return new FileAccessUrlResponse(
                String.valueOf(file.getFileId()),
                accessUrl(file, expires),
                OffsetDateTime.now(CHINA_ZONE).plusSeconds(expires).toString(),
                file.getAccessLevel()
        );
    }

    @Transactional(readOnly = true)
    public InternalValidateFileResponse validateFile(InternalValidateFileRequest request) {
        FileObjectEntity file = requireFile(parseId(request.fileId(), ApiErrorCode.FILE_NOT_FOUND));
        validateFileForUse(
                file,
                parseId(request.ownerId(), ApiErrorCode.FILE_OWNER_MISMATCH),
                request.scene(),
                request.requireStatus(),
                request.maxSize(),
                request.allowedMimeTypes()
        );
        return new InternalValidateFileResponse(true, new ValidatedFileResponse(
                String.valueOf(file.getFileId()),
                String.valueOf(file.getOwnerId()),
                file.getScene(),
                file.getMimeType(),
                file.getFileSize(),
                file.getFileStatus(),
                accessUrl(file, 3600)
        ));
    }

    @Transactional(readOnly = true)
    public InternalBatchValidateFileResponse batchValidateFiles(InternalBatchValidateFileRequest request) {
        List<String> fileIds = request.fileIds();
        int maxCount = request.maxCount() == null ? 9 : request.maxCount();
        if (fileIds.size() > maxCount) {
            throw batchValidateFailed(List.of(Map.of("fileId", "*", "reason", ApiErrorCode.FILE_SIZE_EXCEEDED.reason())));
        }
        List<Long> parsedFileIds = fileIds.stream()
                .map(fileId -> parseId(fileId, ApiErrorCode.FILE_NOT_FOUND))
                .toList();
        Map<Long, FileObjectEntity> files = fileObjectMapper.selectByFileIds(parsedFileIds).stream()
                .collect(Collectors.toMap(FileObjectEntity::getFileId, Function.identity()));
        Long ownerId = parseId(request.ownerId(), ApiErrorCode.FILE_OWNER_MISMATCH);
        List<Map<String, String>> errors = new ArrayList<>();
        List<BatchValidatedFileResponse> validFiles = new ArrayList<>();
        for (String rawFileId : fileIds) {
            Long parsedFileId = parseId(rawFileId, ApiErrorCode.FILE_NOT_FOUND);
            FileObjectEntity file = files.get(parsedFileId);
            if (file == null) {
                errors.add(fileError(rawFileId, ApiErrorCode.FILE_NOT_FOUND));
                continue;
            }
            ApiErrorCode error = validateFileForBatch(file, ownerId, request.scene());
            if (error != null) {
                errors.add(fileError(rawFileId, error));
                continue;
            }
            validFiles.add(new BatchValidatedFileResponse(
                    rawFileId,
                    file.getMimeType(),
                    file.getFileSize(),
                    accessUrl(file, 3600)
            ));
        }
        if (!errors.isEmpty()) {
            throw batchValidateFailed(errors);
        }
        return new InternalBatchValidateFileResponse(true, validFiles);
    }

    @Transactional
    public InternalBindFileResponse bindFile(InternalBindFileRequest request) {
        Long fileId = parseId(request.fileId(), ApiErrorCode.FILE_NOT_FOUND);
        Long ownerId = parseId(request.ownerId(), ApiErrorCode.FILE_OWNER_MISMATCH);
        bind(fileId, ownerId, request.bindType(), request.bindId(), now());
        return new InternalBindFileResponse(request.fileId(), BIND_STATUS_BOUND);
    }

    @Transactional
    public InternalBatchBindFileResponse batchBindFiles(InternalBatchBindFileRequest request) {
        Long ownerId = parseId(request.ownerId(), ApiErrorCode.FILE_OWNER_MISMATCH);
        LocalDateTime now = now();
        List<String> distinctFileIds = new ArrayList<>(new LinkedHashSet<>(request.fileIds()));
        for (String fileId : distinctFileIds) {
            bind(parseId(fileId, ApiErrorCode.FILE_NOT_FOUND), ownerId, request.bindType(), request.bindId(), now);
        }
        return new InternalBatchBindFileResponse(BIND_STATUS_BOUND, distinctFileIds);
    }

    public String publicAccessUrl(String fileId) {
        FileObjectEntity file = requireFile(parseId(fileId, ApiErrorCode.FILE_NOT_FOUND));
        return accessUrl(file, 3600);
    }

    private void bind(Long fileId, Long ownerId, String bindType, String bindId, LocalDateTime now) {
        FileObjectEntity file = requireFile(fileId);
        requireOwner(file, ownerId);
        if (!USABLE_STATUSES.contains(file.getFileStatus())) {
            throw new BusinessException(ApiErrorCode.FILE_STATUS_INVALID);
        }

        FileBindingEntity existing = fileBindingMapper.selectBinding(fileId, bindType, bindId);
        if (existing != null && BIND_STATUS_BOUND.equals(existing.getBindStatus())) {
            fileObjectMapper.markBound(fileId, now, now);
            return;
        }

        boolean inserted = false;
        if (existing == null) {
            FileBindingEntity binding = new FileBindingEntity();
            binding.setId(idGenerator.nextId());
            binding.setFileId(fileId);
            binding.setOwnerId(ownerId);
            binding.setBindType(bindType);
            binding.setBindId(bindId);
            binding.setBindStatus(BIND_STATUS_BOUND);
            binding.setBindVersion(1L);
            binding.setBoundAt(now);
            binding.setCreatedAt(now);
            binding.setUpdatedAt(now);
            try {
                fileBindingMapper.insert(binding);
                inserted = true;
            } catch (DuplicateKeyException exception) {
                inserted = false;
            }
        }
        if (!inserted) {
            FileBindingEntity binding = new FileBindingEntity();
            binding.setOwnerId(ownerId);
            binding.setBoundAt(now);
            binding.setUpdatedAt(now);
            fileBindingMapper.markBound(fileId, bindType, bindId, binding);
        }
        fileObjectMapper.markBound(fileId, now, now);
        FileObjectEntity bound = requireFile(fileId);
        insertFileOutbox("FileBound", bound, boundPayload(bound, bindType, bindId), now);
    }

    private void validateFileForUse(
            FileObjectEntity file,
            Long ownerId,
            String scene,
            List<String> requireStatus,
            Long maxSize,
            List<String> allowedMimeTypes
    ) {
        requireOwner(file, ownerId);
        if (!file.getScene().equals(scene)) {
            throw new BusinessException(ApiErrorCode.FILE_SCENE_INVALID);
        }
        if (requireStatus != null && !requireStatus.isEmpty() && !requireStatus.contains(file.getFileStatus())) {
            throw new BusinessException(ApiErrorCode.FILE_STATUS_INVALID);
        }
        if (maxSize != null && file.getFileSize() > maxSize) {
            throw new BusinessException(ApiErrorCode.FILE_SIZE_EXCEEDED);
        }
        if (allowedMimeTypes != null && !allowedMimeTypes.isEmpty()
                && !allowedMimeTypes.contains(file.getMimeType())) {
            throw new BusinessException(ApiErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
    }

    private ApiErrorCode validateFileForBatch(FileObjectEntity file, Long ownerId, String scene) {
        if (!file.getOwnerId().equals(ownerId)) {
            return ApiErrorCode.FILE_OWNER_MISMATCH;
        }
        if (!file.getScene().equals(scene)) {
            return ApiErrorCode.FILE_SCENE_INVALID;
        }
        if (!USABLE_STATUSES.contains(file.getFileStatus())) {
            return ApiErrorCode.FILE_STATUS_INVALID;
        }
        FileSceneSpec spec = requireSceneSpec(scene);
        if (!spec.allowedMimeTypes().contains(file.getMimeType())) {
            return ApiErrorCode.FILE_TYPE_NOT_SUPPORTED;
        }
        if (file.getFileSize() > spec.maxSize()) {
            return ApiErrorCode.FILE_SIZE_EXCEEDED;
        }
        return null;
    }

    private void validateConfirmedObject(
            FileObjectEntity file,
            ConfirmUploadRequest request,
            StoredObjectInfo objectInfo
    ) {
        Long expectedSize = request.fileSize() == null ? file.getFileSize() : request.fileSize();
        if (objectInfo.size() != expectedSize) {
            throw new BusinessException(ApiErrorCode.UPLOAD_NOT_COMPLETED);
        }
        String requestEtag = normalizeEtag(request.etag());
        if (requestEtag != null && objectInfo.etag() != null && !requestEtag.equals(objectInfo.etag())) {
            throw new BusinessException(ApiErrorCode.UPLOAD_NOT_COMPLETED);
        }
    }

    private ConfirmUploadResponse toConfirmResponse(FileObjectEntity file) {
        return new ConfirmUploadResponse(
                String.valueOf(file.getFileId()),
                file.getFileStatus(),
                file.getScene(),
                accessUrl(file, 3600)
        );
    }

    private FileObjectEntity requireFile(Long fileId) {
        FileObjectEntity file = fileObjectMapper.selectByFileId(fileId);
        if (file == null) {
            throw new BusinessException(ApiErrorCode.FILE_NOT_FOUND);
        }
        return file;
    }

    private void requireOwner(FileObjectEntity file, Long ownerId) {
        if (!file.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ApiErrorCode.FILE_OWNER_MISMATCH);
        }
    }

    private FileSceneSpec requireSceneSpec(String scene) {
        FileSceneSpec spec = SCENE_SPECS.get(scene);
        if (spec == null) {
            throw new BusinessException(ApiErrorCode.FILE_SCENE_INVALID);
        }
        return spec;
    }

    private void validateMimeType(FileSceneSpec spec, String mimeType) {
        if (!spec.allowedMimeTypes().contains(mimeType)) {
            throw new BusinessException(ApiErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
    }

    private void validateSize(FileSceneSpec spec, Long fileSize) {
        if (fileSize == null || fileSize <= 0 || fileSize > spec.maxSize()) {
            throw new BusinessException(ApiErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    private String accessUrl(FileObjectEntity file, int expiresSeconds) {
        if ("PUBLIC".equals(file.getAccessLevel())) {
            return objectStorageClient.publicUrl(file.getBucket(), file.getObjectKey());
        }
        return objectStorageClient.presignedGetUrl(file.getBucket(), file.getObjectKey(), expiresSeconds);
    }

    private String objectKey(FileSceneSpec spec, Long ownerId, Long fileId, String fileExt) {
        LocalDate today = LocalDate.now(CHINA_ZONE);
        return "local/%s/%d/%02d/%02d/%d/%d.%s".formatted(
                spec.pathSegment(),
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                ownerId,
                fileId,
                fileExt
        );
    }

    private void insertFileOutbox(
            String eventType,
            FileObjectEntity file,
            Map<String, Object> payload,
            LocalDateTime now
    ) {
        String eventId = UUID.randomUUID().toString();
        FileOutboxEventEntity entity = new FileOutboxEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateId(file.getFileId());
        entity.setPayload(jsonPayloads.stringify(eventEnvelope(eventId, eventType, "bluenote-file", file.getFileId(), payload)));
        entity.setSendStatus("INIT");
        entity.setRetryCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        outboxEventMapper.insert(entity);
    }

    private Map<String, Object> eventEnvelope(
            String eventId,
            String eventType,
            String producer,
            Long aggregateId,
            Map<String, Object> payload
    ) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", OffsetDateTime.now(CHINA_ZONE).toString());
        envelope.put("traceId", TraceIdHolder.currentOrNew());
        envelope.put("producer", producer);
        envelope.put("bizKey", String.valueOf(aggregateId));
        envelope.put("payload", payload);
        return envelope;
    }

    private Map<String, Object> uploadedPayload(FileObjectEntity file) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileId", String.valueOf(file.getFileId()));
        payload.put("ownerId", String.valueOf(file.getOwnerId()));
        payload.put("scene", file.getScene());
        payload.put("mimeType", file.getMimeType());
        payload.put("fileSize", file.getFileSize());
        payload.put("accessLevel", file.getAccessLevel());
        payload.put("fileStatus", file.getFileStatus());
        return payload;
    }

    private Map<String, Object> boundPayload(FileObjectEntity file, String bindType, String bindId) {
        Map<String, Object> payload = uploadedPayload(file);
        payload.put("bindType", bindType);
        payload.put("bindId", bindId);
        return payload;
    }

    private BusinessException batchValidateFailed(List<Map<String, String>> errors) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", ApiErrorCode.FILE_BATCH_VALIDATE_FAILED.reason());
        data.put("errors", errors);
        return new BusinessException(ApiErrorCode.FILE_BATCH_VALIDATE_FAILED, data);
    }

    private Map<String, String> fileError(String fileId, ApiErrorCode errorCode) {
        return Map.of("fileId", fileId, "reason", errorCode.reason());
    }

    private Long parseId(String value, ApiErrorCode errorCode) {
        try {
            if (value == null || value.isBlank()) {
                throw new NumberFormatException("blank");
            }
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private String normalizeMimeType(String mimeType) {
        return mimeType == null ? "" : mimeType.trim().toLowerCase(Locale.ROOT);
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BusinessException(ApiErrorCode.FILE_TYPE_NOT_SUPPORTED);
        };
    }

    private String normalizeEtag(String etag) {
        if (etag == null || etag.isBlank()) {
            return null;
        }
        return etag.replace("\"", "");
    }

    private int normalizeExpireSeconds(Integer expireSeconds) {
        if (expireSeconds == null) {
            return 3600;
        }
        return Math.max(60, Math.min(expireSeconds, 604800));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CHINA_ZONE);
    }

    private record FileSceneSpec(
            String scene,
            Set<String> allowedMimeTypes,
            long maxSize,
            String accessLevel,
            String pathSegment
    ) {
    }
}
