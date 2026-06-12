package com.bluenote.social.push.api.dto;

public record PushPreferenceUpdateRequest(
        Boolean globalEnabled,
        Boolean interactionEnabled,
        Boolean followEnabled,
        Boolean systemEnabled,
        Boolean orderEnabled,
        Boolean imEnabled,
        Boolean showImDetail,
        Boolean quietHoursEnabled,
        String quietStart,
        String quietEnd
) {
}
