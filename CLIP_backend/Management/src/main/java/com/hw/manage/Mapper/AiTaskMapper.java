package com.hw.manage.Mapper;

import com.hw.pojo.entity.AiTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiTaskMapper {
    @Insert("INSERT INTO ai_tasks(user_id, task_type, status, error_message) " +
            "VALUES(#{userId}, #{taskType}, #{status}, #{errorMessage})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiTask task);

    @Select("SELECT * FROM ai_tasks WHERE id = #{id} AND user_id = #{userId}")
    AiTask findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM ai_tasks WHERE id = #{id}")
    AiTask findById(@Param("id") Long id);

    @Update("UPDATE ai_tasks SET status = #{status}, error_message = #{errorMessage} WHERE id = #{id}")
    void updateStatus(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("errorMessage") String errorMessage);

    @Insert("INSERT IGNORE INTO ai_task_photos(task_id, photo_id) VALUES(#{taskId}, #{photoId})")
    void addPhoto(@Param("taskId") Long taskId, @Param("photoId") Long photoId);

    @Insert("INSERT INTO ai_task_descriptions(task_id, description_id) VALUES(#{taskId}, #{descriptionId})")
    void addDescription(@Param("taskId") Long taskId, @Param("descriptionId") Long descriptionId);
}
