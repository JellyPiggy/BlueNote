package com.bluenote.social.relation.api.dto;

import java.util.List;

public record BatchFollowStatusResponse(List<FollowStatusItem> items) {
}
