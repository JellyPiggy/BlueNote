package com.bluenote.content.comment.infrastructure.mapper;

import com.bluenote.content.comment.infrastructure.entity.CommentOutboxEventEntity;

public interface CommentOutboxEventMapper {

    int insert(CommentOutboxEventEntity entity);
}
