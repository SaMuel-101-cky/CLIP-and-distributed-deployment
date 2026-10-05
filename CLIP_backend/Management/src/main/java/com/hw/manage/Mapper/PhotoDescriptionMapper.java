package com.hw.manage.Mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import java.util.List;

@Mapper
public interface PhotoDescriptionMapper {
    // 根据描述ID查匹配的图片ID
    @Select("SELECT photo_id FROM photo_description_matches " +
            "WHERE task_id = #{taskId} AND description_id = #{descriptionId} " +
            "ORDER BY rank_no ASC")
    List<Long> findPhotoIdsByTaskAndDescription(@Param("taskId") Long taskId,
                                                @Param("descriptionId") Long descriptionId);

    @Select("SELECT count(*) FROM photo_description_matches WHERE task_id = #{taskId}")
    Integer countMatchesByTask(Long taskId);

    @Select("SELECT count(*) FROM ai_task_photos WHERE task_id = #{taskId} AND photo_id = #{photoId}")
    Integer countTaskPhoto(@Param("taskId") Long taskId, @Param("photoId") Long photoId);

    @Select("SELECT count(*) FROM ai_task_descriptions WHERE task_id = #{taskId} AND description_id = #{descriptionId}")
    Integer countTaskDescription(@Param("taskId") Long taskId, @Param("descriptionId") Long descriptionId);

    @Insert("INSERT INTO photo_description_matches(task_id, photo_id, description_id, match_type, score, rank_no) " +
            "VALUES(#{taskId}, #{photoId}, #{descriptionId}, #{matchType}, #{score}, #{rankNo}) " +
            "ON DUPLICATE KEY UPDATE score = VALUES(score), rank_no = VALUES(rank_no)")
    void upsertMatch(@Param("taskId") Long taskId,
                     @Param("photoId") Long photoId,
                     @Param("descriptionId") Long descriptionId,
                     @Param("matchType") String matchType,
                     @Param("score") Double score,
                     @Param("rankNo") Integer rankNo);
}
