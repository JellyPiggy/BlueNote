package com.bluenote.content.comment.infrastructure.mapper;

import com.bluenote.content.comment.infrastructure.entity.CommentContentEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CommentContentMapper {

    int insert(CommentContentEntity entity);

    CommentContentEntity selectByCommentId(@Param("commentId") Long commentId);

    List<CommentContentEntity> selectByCommentIds(@Param("commentIds") List<Long> commentIds);
}
