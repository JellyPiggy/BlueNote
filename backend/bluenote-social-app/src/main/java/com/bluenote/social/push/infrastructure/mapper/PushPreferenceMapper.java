package com.bluenote.social.push.infrastructure.mapper;

import com.bluenote.social.push.infrastructure.entity.PushPreferenceEntity;

public interface PushPreferenceMapper {

    int insertIgnore(PushPreferenceEntity entity);

    int update(PushPreferenceEntity entity);

    PushPreferenceEntity selectByUserId(Long userId);
}
