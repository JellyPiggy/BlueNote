package com.bluenote.member.user.api.dto;

import java.util.List;

public record StatusCheckResponse(List<StatusCheckItem> results) {
}
