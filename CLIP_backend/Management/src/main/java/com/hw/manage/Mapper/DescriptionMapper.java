package com.hw.manage.Mapper;

import com.hw.pojo.entity.Description;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface DescriptionMapper {
    @Insert("INSERT INTO descriptions(user_id, content, text_type) " +
            "VALUES(#{userId}, #{content}, #{textType})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Description description);

    @Select("SELECT d.* FROM descriptions d " +
            "JOIN ai_task_descriptions td ON d.id = td.description_id " +
            "WHERE d.user_id = #{userId} AND td.task_id = #{taskId} ORDER BY d.id ASC")
    List<Description> findByUserAndTaskId(@Param("userId") Long userId, @Param("taskId") Long taskId);
}
