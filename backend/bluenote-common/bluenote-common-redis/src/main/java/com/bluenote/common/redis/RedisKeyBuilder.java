package com.bluenote.common.redis;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public final class RedisKeyBuilder {

    private static final String PREFIX = "bluenote";

    private RedisKeyBuilder() {
    }

    public static String build(String env, String service, String biz, Object... parts) {
        String suffix = Arrays.stream(parts)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.joining(":"));
        if (suffix.isBlank()) {
            return String.join(":", PREFIX, env, service, biz);
        }
        return String.join(":", PREFIX, env, service, biz, suffix);
    }
}
