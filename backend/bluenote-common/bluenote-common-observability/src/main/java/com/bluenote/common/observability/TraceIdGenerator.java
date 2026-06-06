package com.bluenote.common.observability;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class TraceIdGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private TraceIdGenerator() {
    }

    public static String generate() {
        String time = OffsetDateTime.now(ZoneOffset.ofHours(8)).format(FORMATTER);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "trace-" + time + "-" + suffix;
    }
}
