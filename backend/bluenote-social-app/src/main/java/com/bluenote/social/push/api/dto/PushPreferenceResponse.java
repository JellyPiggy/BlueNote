package com.bluenote.social.push.api.dto;

public record PushPreferenceResponse(
        boolean globalEnabled,
        boolean interactionEnabled,
        boolean followEnabled,
        boolean systemEnabled,
        boolean orderEnabled,
        boolean imEnabled,
        boolean showImDetail,
        boolean quietHoursEnabled,
        String quietStart,
        String quietEnd,
        String updatedAt
) {
}
