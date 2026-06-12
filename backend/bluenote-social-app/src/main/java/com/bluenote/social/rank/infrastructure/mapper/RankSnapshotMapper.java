package com.bluenote.social.rank.infrastructure.mapper;

import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankSnapshotEntity;
import com.bluenote.social.rank.infrastructure.entity.RankEntities.RankSnapshotItemEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RankSnapshotMapper {

    int insertSnapshot(RankSnapshotEntity entity);

    int insertSnapshotItem(RankSnapshotItemEntity entity);

    RankSnapshotEntity selectLatest(
            @Param("rankCode") String rankCode,
            @Param("periodId") String periodId,
            @Param("snapshotType") String snapshotType
    );

    List<RankSnapshotItemEntity> selectItems(@Param("snapshotId") Long snapshotId);
}
