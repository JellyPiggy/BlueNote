package com.bluenote.common.core;

import java.util.Map;

public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(ApiErrorCode.SUCCESS.code(), ApiErrorCode.SUCCESS.message(), data, traceId);
    }

    public static ApiResponse<Object> failure(ErrorCode errorCode, String traceId) {
        return failure(errorCode, Map.of("reason", errorCode.reason()), traceId);
    }

    public static ApiResponse<Object> failure(ErrorCode errorCode, Object data, String traceId) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), data, traceId);
    }
}
