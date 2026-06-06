package com.bluenote.common.security;

public record UserContext(String userId, String deviceId, String sessionId) {

    public boolean authenticated() {
        return userId != null && !userId.isBlank();
    }
}
