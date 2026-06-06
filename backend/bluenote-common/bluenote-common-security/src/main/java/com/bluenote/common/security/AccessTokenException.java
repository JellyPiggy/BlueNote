package com.bluenote.common.security;

import com.bluenote.common.core.ApiErrorCode;

public class AccessTokenException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public AccessTokenException(ApiErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ApiErrorCode errorCode() {
        return errorCode;
    }
}
