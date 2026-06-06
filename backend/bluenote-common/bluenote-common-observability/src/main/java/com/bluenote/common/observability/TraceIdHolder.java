package com.bluenote.common.observability;

public final class TraceIdHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceIdHolder() {
    }

    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    public static String current() {
        return HOLDER.get();
    }

    public static String currentOrNew() {
        String traceId = HOLDER.get();
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdGenerator.generate();
            HOLDER.set(traceId);
        }
        return traceId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
