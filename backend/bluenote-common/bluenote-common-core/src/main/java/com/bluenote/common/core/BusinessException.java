package com.bluenote.common.core;

import java.util.Map;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object errorData;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, Map.of("reason", errorCode.reason()));
    }

    public BusinessException(ErrorCode errorCode, Object errorData) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.errorData = errorData;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object errorData() {
        return errorData;
    }
}
