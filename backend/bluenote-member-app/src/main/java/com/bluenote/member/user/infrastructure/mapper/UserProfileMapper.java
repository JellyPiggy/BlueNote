package com.bluenote.member.user.infrastructure.mapper;

import com.bluenote.member.user.infrastructure.entity.UserProfileEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserProfileMapper {

    int insert(UserProfileEntity entity);

    UserProfileEntity selectByUserId(@Param("userId") Long userId);

    List<UserProfileEntity> selectByUserIds(@Param("userIds") List<Long> userIds);

    int updateByVersion(
            @Param("entity") UserProfileEntity entity,
            @Param("baseProfileVersion") Long baseProfileVersion
    );
}
