package com.bluenote.member.user.api.dto;

import java.util.List;

public record BatchUserSummaryResponse(List<UserSummaryItem> users) {
}
