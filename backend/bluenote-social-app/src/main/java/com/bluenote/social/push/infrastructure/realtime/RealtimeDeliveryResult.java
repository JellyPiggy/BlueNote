package com.bluenote.social.push.infrastructure.realtime;

public record RealtimeDeliveryResult(
        boolean delivered,
        String connectionId,
        String errorMessage
) {
    public static RealtimeDeliveryResult delivered(String connectionId) {
        return new RealtimeDeliveryResult(true, connectionId, null);
    }

    public static RealtimeDeliveryResult failed(String errorMessage) {
        return new RealtimeDeliveryResult(false, null, errorMessage);
    }
}
