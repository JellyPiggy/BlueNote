package com.bluenote.content.comment.infrastructure.mapper;

import com.bluenote.content.comment.infrastructure.entity.CommentOperationLogEntity;

public interface CommentOperationLogMapper {

    int insert(CommentOperationLogEntity entity);
}
